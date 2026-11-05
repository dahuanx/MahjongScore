// mjserver — 麻将计分器联机中转服务 v0.1
// 职责：房间管理 + 记分流水权威存储 + WebSocket 实时广播
// 端口：默认 9000，可用环境变量 MJ_PORT 覆盖
package main

import (
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// ---------- 数据结构 ----------

type Player struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Seat   int    `json:"seat"` // 0-3
	Host   bool   `json:"host"`
	Online bool   `json:"online"`
	Bot    bool   `json:"bot,omitempty"`
	Wind   string `json:"wind,omitempty"` // 吊风结果：东风/南风/西风/北风
}

type Round struct {
	Seq    int64    `json:"seq"`
	Winner string   `json:"winner"`
	Kind   string   `json:"kind"`   // pinghu/dianpao/zimo/loubao/baozhongbao
	Points int64    `json:"points"` // 每家应付分数
	Payers []string `json:"payers"` // 付款方（点炮1家，其余3家）
	Loser  string   `json:"loser,omitempty"` // 兼容旧数据
	TS     int64    `json:"ts"`
}

// 胡型分值表（每家应付，默认值）
var scoreTable = map[string]int64{
	"pinghu":      5,
	"dianpao":     20,
	"zimo":        10,
	"loubao":      20,
	"baozhongbao": 40,
}

// 房间实际生效的分值：自定义表优先，缺项回退默认
func roomScore(room *Room, kind string) (int64, bool) {
	if room.Scores != nil {
		if v, ok := room.Scores[kind]; ok {
			return v, true
		}
	}
	v, ok := scoreTable[kind]
	return v, ok
}

// 单值版本（kind 已经过 play 校验，必然存在）
func mustScore(room *Room, kind string) int64 {
	v, _ := roomScore(room, kind)
	return v
}

// 展开完整生效分值表（广播 / state 用，客户端无脑覆盖）
func effectiveScores(room *Room) map[string]int64 {
	eff := make(map[string]int64, len(scoreTable))
	for k, v := range scoreTable {
		eff[k] = v
	}
	for k, v := range room.Scores {
		eff[k] = v
	}
	return eff
}

// 校验房主提交的分值表：只认五种胡型，分值 clamp 到 1..999
func sanitizeScores(in map[string]int64) map[string]int64 {
	out := map[string]int64{}
	for k, v := range in {
		if _, ok := scoreTable[k]; !ok {
			continue
		}
		if v < 1 {
			v = 1
		}
		if v > 999 {
			v = 999
		}
		out[k] = v
	}
	return out
}

func (r *Room) balances() map[string]int64 {
	b := map[string]int64{}
	for _, p := range r.Players {
		b[p.ID] = 0
	}
	for _, rd := range r.Rounds {
		payers := rd.Payers
		if len(payers) == 0 && rd.Loser != "" { // 旧数据兼容
			payers = []string{rd.Loser}
		}
		for _, pid := range payers {
			b[pid] -= rd.Points
		}
		b[rd.Winner] += rd.Points * int64(len(payers))
	}
	return b
}

type Room struct {
	Code      string         `json:"code"`
	CreatedAt int64          `json:"created_at"` // 开局时间（秒），回房 12 小时窗口以此起算
	Closed    bool           `json:"closed"`
	Players   []Player       `json:"players"`
	Rounds    []Round        `json:"rounds"`
	NextSeq   int64          `json:"next_seq"`
	Scores    map[string]int64 `json:"scores,omitempty"` // 房间自定义分值表；nil = 用默认
}

func (r *Room) nextSeat() int {
	used := map[int]bool{}
	for _, p := range r.Players {
		used[p.Seat] = true
	}
	for s := 0; s < 4; s++ {
		if !used[s] {
			return s
		}
	}
	return -1
}

func (r *Room) findPlayer(id string) *Player {
	for i := range r.Players {
		if r.Players[i].ID == id {
			return &r.Players[i]
		}
	}
	return nil
}

// ---------- 消息协议 ----------

// 客户端 -> 服务端
type C2S struct {
	Type string `json:"type"` // join / play / undo / setScores
	Room string `json:"room,omitempty"`
	PlayerID string `json:"player_id,omitempty"`
	Name string `json:"name,omitempty"`
	Winner string `json:"winner,omitempty"`
	Loser  string `json:"loser,omitempty"`
	Points int64  `json:"points,omitempty"`
	Kind   string `json:"kind,omitempty"`
	Seq    int64  `json:"seq,omitempty"`
	Scores map[string]int64 `json:"scores,omitempty"`
}

// 服务端 -> 客户端
type S2C struct {
	Type string `json:"type"` // state / round / undid / peers / error
	Room string      `json:"room,omitempty"`
	You  string      `json:"you,omitempty"`
	Host string      `json:"host,omitempty"`
	RoomStart int64  `json:"room_start,omitempty"` // 房间开局时间（秒）
	Closed    bool   `json:"closed,omitempty"`     // 房间已被房主结束
	Players []Player `json:"players,omitempty"`
	Rounds  []Round  `json:"rounds,omitempty"`
	Balances map[string]int64 `json:"balances,omitempty"`
	Scores map[string]int64 `json:"scores,omitempty"`
	Msg string `json:"msg,omitempty"`
}

// ---------- 连接与房间管理 ----------

type Client struct {
	conn *websocket.Conn
	send chan []byte
	room *Room
	pid  string
}

type Hub struct {
	mu    sync.Mutex
	rooms map[string]*Room
	clients map[*Client]bool
	persistPath string
}

func newHub(persistPath string) *Hub {
	h := &Hub{rooms: map[string]*Room{}, clients: map[*Client]bool{}, persistPath: persistPath}
	h.load()
	return h
}

func (h *Hub) load() {
	data, err := os.ReadFile(h.persistPath)
	if err != nil {
		return
	}
	var rooms []*Room
	if err := json.Unmarshal(data, &rooms); err != nil {
		log.Printf("载入持久化数据失败: %v", err)
		return
	}
	for _, r := range rooms {
		for i := range r.Players {
			r.Players[i].Online = false
		}
		if r.CreatedAt == 0 {
			r.CreatedAt = time.Now().Unix()
		}
		h.rooms[r.Code] = r
	}
	log.Printf("已载入 %d 个房间", len(rooms))
}

func (h *Hub) persist() {
	rooms := make([]*Room, 0, len(h.rooms))
	for _, r := range h.rooms {
		rooms = append(rooms, r)
	}
	data, err := json.MarshalIndent(rooms, "", " ")
	if err == nil {
		tmp := h.persistPath + ".tmp"
		if err := os.WriteFile(tmp, data, 0644); err == nil {
			os.Rename(tmp, h.persistPath)
		}
	}
}

func genRoomCode() string {
	return strconv.FormatInt(time.Now().UnixNano()%100000, 10) // 5位数字房号
}

func (h *Hub) broadcast(room *Room, msg S2C, exclude *Client) {
	data, _ := json.Marshal(msg)
	for c := range h.clients {
		if c.room == room && c != exclude {
			select {
			case c.send <- data:
			default:
			}
		}
	}
}

// ---------- WebSocket 处理 ----------

var upgrader = websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}

const (
	writeWait  = 10 * time.Second
	pongWait   = 60 * time.Second
	pingPeriod = 25 * time.Second
)

func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() { ticker.Stop(); c.conn.Close() }()
	for {
		select {
		case data, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.conn.WriteMessage(websocket.TextMessage, data); err != nil {
				return
			}
		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (h *Hub) handleWS(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	c := &Client{conn: conn, send: make(chan []byte, 32)}
	h.mu.Lock()
	h.clients[c] = true
	h.mu.Unlock()

	defer func() {
		h.mu.Lock()
		delete(h.clients, c)
		if c.room != nil && c.pid != "" {
			if p := c.room.findPlayer(c.pid); p != nil {
				p.Online = false
			}
			h.broadcast(c.room, S2C{Type: "peers", Room: c.room.Code, Players: c.room.Players}, nil)
			h.persist()
		}
		h.mu.Unlock()
		conn.Close()
	}()

	go c.writePump()

	conn.SetReadDeadline(time.Now().Add(pongWait))
	conn.SetPongHandler(func(string) error { conn.SetReadDeadline(time.Now().Add(pongWait)); return nil })

	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			return
		}
		var m C2S
		if err := json.Unmarshal(raw, &m); err != nil {
			h.sendErr(c, "消息格式错误")
			continue
		}
		h.handle(c, m)
	}
}

func (h *Hub) sendErr(c *Client, msg string) {
	data, _ := json.Marshal(S2C{Type: "error", Msg: msg})
	select {
	case c.send <- data:
	default:
	}
}

func (h *Hub) sendState(c *Client, room *Room) {
	host := ""
	for _, p := range room.Players {
		if p.Host {
			host = p.ID
		}
	}
	data, _ := json.Marshal(S2C{Type: "state", Room: room.Code, You: c.pid, Host: host,
		RoomStart: room.CreatedAt, Closed: room.Closed,
		Players: room.Players, Rounds: room.Rounds, Balances: room.balances(),
		Scores: effectiveScores(room)})
	select {
	case c.send <- data:
	default:
	}
}

func (h *Hub) handle(c *Client, m C2S) {
	h.mu.Lock()
	defer h.mu.Unlock()

	switch m.Type {
	case "join":
		code := m.Room
		if code == "" {
			// 建房
			for tries := 0; tries < 10; tries++ {
				code = genRoomCode()
				if _, ok := h.rooms[code]; !ok {
					break
				}
			}
			h.rooms[code] = &Room{Code: code, NextSeq: 1, CreatedAt: time.Now().Unix()}
			// 房主建房时带上自己的分值偏好
			if len(m.Scores) > 0 {
				h.rooms[code].Scores = sanitizeScores(m.Scores)
			}
		}
		room, ok := h.rooms[code]
		if !ok {
			h.sendErr(c, "房间不存在")
			return
		}
		if room.Closed {
			h.sendErr(c, "对局已结束，无法再加入")
			return
		}
		if room.CreatedAt > 0 && time.Now().Unix()-room.CreatedAt > 24*3600 {
			// 超 12 小时自动关门
			room.Closed = true
			for cl := range h.clients {
				if cl.room == room {
					h.sendState(cl, room)
				}
			}
			delete(h.rooms, code)
			h.persist()
			h.sendErr(c, "对局已超过24小时，已自动结束")
			return
		}
		p := room.findPlayer(m.PlayerID)
		if p == nil {
			seat := room.nextSeat()
			if seat >= 0 {
				room.Players = append(room.Players, Player{ID: m.PlayerID, Name: m.Name, Seat: seat, Host: len(room.Players) == 0, Online: true})
			} else {
				// 满员：顶替"人走了但座位还在"的位置
				// 优先非房主的离线位；全房都没人在线时，连房主位一起接（新人接任房主）
				idx, inheritHost := -1, false
				for i := range room.Players {
					q := room.Players[i]
					if !q.Online && !q.Host && !q.Bot {
						idx = i
						break
					}
				}
				if idx < 0 {
					anyOnline := false
					for i := range room.Players {
						if room.Players[i].Online {
							anyOnline = true
							break
						}
					}
					if !anyOnline {
						for i := range room.Players {
							if !room.Players[i].Bot {
								idx = i
								inheritHost = room.Players[i].Host
								break
							}
						}
					}
				}
				if idx < 0 {
					h.sendErr(c, "房间已满（4人）")
					return
				}
				// 换人坐这个位子：风位随座位继承（东风位换人还是东风），保持东南西北齐全
				room.Players[idx] = Player{
					ID:     m.PlayerID,
					Name:   m.Name,
					Seat:   room.Players[idx].Seat,
					Host:   inheritHost,
					Online: true,
					Wind:   room.Players[idx].Wind,
				}
			}
		} else {
			if m.Name != "" {
				p.Name = m.Name
			}
			p.Online = true
		}
		c.room = room
		c.pid = m.PlayerID
		h.sendState(c, room)
		h.broadcast(room, S2C{Type: "peers", Room: room.Code, Players: room.Players}, c)
		h.persist()

	case "setScores":
		room := c.room
		if room == nil {
			h.sendErr(c, "尚未加入房间")
			return
		}
		host := ""
		for _, p := range room.Players {
			if p.Host {
				host = p.ID
			}
		}
		if c.pid != host {
			h.sendErr(c, "只有房主能修改计分规则")
			return
		}
		if len(m.Scores) > 0 {
			room.Scores = sanitizeScores(m.Scores)
		} else {
			room.Scores = nil // 空 = 恢复默认
		}
		h.persist()
		h.broadcast(room, S2C{Type: "scores", Room: room.Code, Scores: effectiveScores(room)}, nil)
		h.sendState(c, room)

	case "play":
		room := c.room
		if room == nil {
			h.sendErr(c, "尚未加入房间")
			return
		}
		if len(room.Players) < 4 {
			h.sendErr(c, "坐满4人才能开始记分")
			return
		}
		if m.Winner == "" || room.findPlayer(m.Winner) == nil {
			h.sendErr(c, "赢家无效")
			return
		}
		pts, ok := roomScore(room, m.Kind)
		if !ok {
			h.sendErr(c, "胡型无效")
			return
		}
		var payers []string
		if m.Kind == "dianpao" {
			// 点炮：只有操作者（输家）单付
			if c.pid == m.Winner {
				h.sendErr(c, "点炮须由输家操作")
				return
			}
			payers = []string{c.pid}
		} else {
			// 其余胡型：除赢家外其余三家全付
			for _, p := range room.Players {
				if p.ID != m.Winner {
					payers = append(payers, p.ID)
				}
			}
		}
		if len(payers) == 0 {
			h.sendErr(c, "人数不足，无法记分")
			return
		}
		rd := Round{Seq: room.NextSeq, Winner: m.Winner, Kind: m.Kind, Points: pts, Payers: payers, TS: time.Now().Unix()}
		room.NextSeq++
		room.Rounds = append(room.Rounds, rd)
		h.broadcast(room, S2C{Type: "round", Room: room.Code, Rounds: []Round{rd}, Balances: room.balances()}, nil)
		if c.room != nil {
			h.sendState(c, room) // 记分者自己也确认一遍
		}
		h.persist()

	case "undo":
		room := c.room
		if room == nil {
			h.sendErr(c, "尚未加入房间")
			return
		}
		host := ""
		for _, p := range room.Players {
			if p.Host {
				host = p.ID
			}
		}
		if c.pid != host {
			h.sendErr(c, "仅房主可撤销")
			return
		}
		found := false
		for i := len(room.Rounds) - 1; i >= 0; i-- {
			if room.Rounds[i].Seq == m.Seq {
				room.Rounds = append(room.Rounds[:i], room.Rounds[i+1:]...)
				found = true
				break
			}
		}
		if !found {
			h.sendErr(c, "流水序号不存在")
			return
		}
		h.broadcast(room, S2C{Type: "undid", Room: room.Code, Rounds: room.Rounds, Balances: room.balances()}, nil)
		h.persist()

	case "wind":
		// 吊风：把东/南/西/北随机分给房间里的四个人，全房同步
		room := c.room
		if room == nil {
			h.sendErr(c, "尚未加入房间")
			return
		}
		if len(room.Players) < 4 {
			h.sendErr(c, "坐满4人才能吊风")
			return
		}
		winds := []string{"东风", "南风", "西风", "北风"}
		rand.Shuffle(len(winds), func(i, j int) {
			winds[i], winds[j] = winds[j], winds[i]
		})
		for i := range room.Players {
			room.Players[i].Wind = ""
		}
		// 按座位顺序发风，保证每家卡片上的风位顺序一致
		order := make([]int, len(room.Players))
		for i := range order {
			order[i] = i
		}
		sort.Slice(order, func(a, b int) bool {
			return room.Players[order[a]].Seat < room.Players[order[b]].Seat
		})
		for n, idx := range order {
			if n < len(winds) {
				room.Players[idx].Wind = winds[n]
			}
		}
		for cl := range h.clients {
			if cl.room == room {
				h.sendState(cl, room)
			}
		}
		h.persist()

	case "end":
		// 房主结束对局：广播"已结束"的全量状态后立即销毁房间，之后任何人无法再加入
		room := c.room
		if room == nil {
			h.sendErr(c, "尚未加入房间")
			return
		}
		if c.pid != roomHostID(room) {
			h.sendErr(c, "仅房主可结束对局")
			return
		}
		room.Closed = true
		for cl := range h.clients {
			if cl.room == room {
				cl.room = nil
				h.sendState(cl, room)
			}
		}
		delete(h.rooms, room.Code)
		h.persist()

	default:
		h.sendErr(c, "未知指令: "+m.Type)
	}
}

// roomHostID 返回房间房主的 playerId
func roomHostID(room *Room) string {
	for _, p := range room.Players {
		if p.Host {
			return p.ID
		}
	}
	return ""
}

// ---------- 版本 ----------

const serverVersion = "0.8.2"

// ---------- 健康检查 ----------

func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, `{"status":"ok","app":"mjserver","version":"%s"}`, serverVersion)
}

// ---------- App 更新 ----------
//
// 部署目录（默认 ./dist，可用 MJ_DIST 覆盖）放两个文件：
//   version.json  {"version":"0.5.2","versionCode":16,"notes":"..."}
//   mahjongscore.apk
// GET /app/version 返回版本描述（url 按请求 Host 拼，不写死 IP）
// GET /app/latest.apk 返回安装包

type appVersion struct {
	Version     string `json:"version"`
	VersionCode int    `json:"versionCode"`
	Notes       string `json:"notes"`
}

// 更新包目录。优先级：MJ_DIST > 数据文件同级的 dist 目录。
// 不依赖进程 cwd，systemd 无论从哪启动都能找到。
var distPath = "dist"

func distDir() string { return distPath }

// 浏览器版（Web 客户端）页面目录。优先级：MJ_WEB > 数据文件同级的 web 目录。
// 每次请求现读文件，改页面只需替换文件，不用重启服务。
var webPath = "web"

func webDir() string { return webPath }

func handleWeb(w http.ResponseWriter, r *http.Request) {
	b, err := os.ReadFile(filepath.Join(webDir(), "index.html"))
	if err != nil {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.WriteHeader(http.StatusNotFound)
		fmt.Fprint(w, "web 版页面未部署：请在服务器 web/ 目录放 index.html")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.Write(b)
}

func handleAppVersion(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	b, err := os.ReadFile(filepath.Join(distDir(), "version.json"))
	if err != nil {
		// 没放版本文件：返回空版本，App 视为"无更新"，不打扰用户
		fmt.Fprint(w, `{"version":"","versionCode":0,"url":"","size":0,"notes":""}`)
		return
	}
	var v appVersion
	if err := json.Unmarshal(b, &v); err != nil || v.Version == "" {
		fmt.Fprint(w, `{"version":"","versionCode":0,"url":"","size":0,"notes":""}`)
		return
	}
	var size int64
	if st, err := os.Stat(filepath.Join(distDir(), "mahjongscore.apk")); err == nil {
		size = st.Size()
	}
	scheme := "http"
	if r.TLS != nil {
		scheme = "https"
	}
	json.NewEncoder(w).Encode(map[string]any{
		"version":     v.Version,
		"versionCode": v.VersionCode,
		"size":        size,
		"notes":       v.Notes,
		"url":         scheme + "://" + r.Host + "/app/latest.apk",
	})
}

func handleAppApk(w http.ResponseWriter, r *http.Request) {
	p := filepath.Join(distDir(), "mahjongscore.apk")
	if _, err := os.Stat(p); err != nil {
		http.Error(w, "apk not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/vnd.android.package-archive")
	http.ServeFile(w, r, p)
}

// ---------- 启动 ----------

func main() {
	port := os.Getenv("MJ_PORT")
	if port == "" {
		port = "9000"
	}
	persistPath := os.Getenv("MJ_DATA")
	if persistPath == "" {
		persistPath = "mjserver.json"
	}
	distPath = os.Getenv("MJ_DIST")
	if distPath == "" {
		distPath = filepath.Join(filepath.Dir(persistPath), "dist")
	}
	webPath = os.Getenv("MJ_WEB")
	if webPath == "" {
		webPath = filepath.Join(filepath.Dir(persistPath), "web")
	}
	hub := newHub(persistPath)
	http.HandleFunc("/health", handleHealth)
	http.HandleFunc("/ws", hub.handleWS)
	http.HandleFunc("/app/version", handleAppVersion)
	http.HandleFunc("/app/latest.apk", handleAppApk)
	http.HandleFunc("/m", handleWeb)
	http.HandleFunc("/m/", handleWeb)
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/" {
			http.Redirect(w, r, "/m", http.StatusFound)
			return
		}
		http.NotFound(w, r)
	})
	addr := ":" + port
	log.Printf("mjserver v%s 监听 %s（数据: %s，更新包: %s，网页: %s）", serverVersion, addr, persistPath, distDir(), webDir())
	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatal(err)
	}
}
