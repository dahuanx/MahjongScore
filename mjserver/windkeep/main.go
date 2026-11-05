// 风位保持测试
// 1) 同一人离场再回房：风位不变
// 2) 换人坐这个位子：风位随座位继承
// 3) 重新吊风：四个风位重新分配
package main

import (
	"fmt"
	"log"
	"time"

	"github.com/gorilla/websocket"
)

type S2C struct {
	Type    string           `json:"type"`
	Room    string           `json:"room"`
	You     string           `json:"you"`
	Players  []map[string]any `json:"players"`
	Balances map[string]int64 `json:"balances"`
	Msg      string           `json:"msg"`
}

func dialJoin(room, pid, name string) (*websocket.Conn, S2C) {
	c, _, err := websocket.DefaultDialer.Dial("ws://127.0.0.1:19000/ws", nil)
	if err != nil {
		log.Fatal(err)
	}
	m := map[string]any{"type": "join", "player_id": pid, "name": name}
	if room != "" {
		m["room"] = room
	}
	c.WriteJSON(m)
	return c, readUntil(c, "state", "error")
}

func readUntil(c *websocket.Conn, wants ...string) S2C {
	c.SetReadDeadline(time.Now().Add(5 * time.Second))
	for {
		var m S2C
		if err := c.ReadJSON(&m); err != nil {
			log.Fatal("recv: ", err)
		}
		for _, w := range wants {
			if m.Type == w {
				return m
			}
		}
	}
}

func winds(m S2C) map[string]string {
	out := map[string]string{}
	for _, p := range m.Players {
		w, _ := p["wind"].(string)
		out[p["id"].(string)] = w
	}
	return out
}

func main() {
	h, st := dialJoin("", "k1", "甲")
	room := st.Room
	var conns []*websocket.Conn
	for i := 2; i <= 4; i++ {
		c, _ := dialJoin(room, fmt.Sprintf("k%d", i), fmt.Sprintf("玩家%d", i))
		conns = append(conns, c)
	}
	fmt.Println("4人建房 OK:", room)

	h.WriteJSON(map[string]any{"type": "wind"})
	w1 := winds(readUntil(h, "state"))
	fmt.Println("初次吊风:", w1)

	// k2 离场
	conns[0].Close()
	time.Sleep(300 * time.Millisecond)
	// 1) 同一人回房
	c2b, st2 := dialJoin(room, "k2", "玩家2")
	w2 := winds(st2)
	if w2["k2"] != w1["k2"] {
		log.Fatalf("❌ 同一人回房风位变了: %q → %q", w1["k2"], w2["k2"])
	}
	fmt.Println("✅ 同一人回房风位保留:", w2["k2"])

	// 2) k2 再次离场，换新人 k9 顶替
	c2b.Close()
	time.Sleep(300 * time.Millisecond)
	_, st3 := dialJoin(room, "k9", "新来的")
	if st3.Type == "error" {
		log.Fatalf("❌ 换人加入被拒: %s", st3.Msg)
	}
	w3 := winds(st3)
	if st3.Players == nil || len(st3.Players) != 4 {
		log.Fatalf("❌ 换人后人数不对: %d", len(st3.Players))
	}
	if _, ok := w3["k9"]; !ok {
		log.Fatalf("❌ 新人不在名单里")
	}
	if w3["k9"] != w1["k2"] {
		log.Fatalf("❌ 新人应继承座位风位 %q，实际 %q", w1["k2"], w3["k9"])
	}
	fmt.Println("✅ 换人顶替座位：继承原风位", w3["k9"])

	// 四家风位仍齐全且不重复
	seenNow := map[string]int{}
	for _, w := range w3 {
		if w != "" {
			seenNow[w]++
		}
	}
	if len(seenNow) != 4 {
		log.Fatalf("❌ 换人后风位不齐: %v", w3)
	}
	for w, n := range seenNow {
		if n != 1 {
			log.Fatalf("❌ 风位重复: %s x%d (%v)", w, n, w3)
		}
	}
	fmt.Println("✅ 换人后东南西北仍齐全不重复")

	// 其余三人风位不应被动过
	for _, id := range []string{"k1", "k3", "k4"} {
		if w3[id] != w1[id] {
			log.Fatalf("❌ %s 的风位被改了: %q → %q", id, w1[id], w3[id])
		}
	}
	fmt.Println("✅ 其余三人的风位未受影响")

	// 3) 重新吊风
	h.WriteJSON(map[string]any{"type": "wind"})
	w4 := winds(readUntil(h, "state"))
	valid := map[string]bool{"东风": true, "南风": true, "西风": true, "北风": true}
	seen := map[string]bool{}
	for id, w := range w4 {
		if !valid[w] || seen[w] {
			log.Fatalf("❌ 重新吊风异常: %v", w4)
		}
		seen[w] = true
		_ = id
	}
	fmt.Println("✅ 重新吊风 OK:", w4)

	// 4) 记分仍然正常
	h.WriteJSON(map[string]any{"type": "play", "winner": "k1", "kind": "zimo"})
	m := readUntil(h, "round")
	if m.Balances == nil {
		log.Fatal("❌ 记分无返回")
	}
	fmt.Println("✅ 换人后记分 OK:", m.Balances)

	fmt.Println("全部通过")
}
