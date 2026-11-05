// 吊风测试：四人各得一个不重复的风位
package main

import (
	"fmt"
	"log"
	"time"

	"github.com/gorilla/websocket"
)

type S2C struct {
	Type     string           `json:"type"`
	Room     string           `json:"room"`
	You      string           `json:"you"`
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
	return c, readUntil(c, "state")
}

func readUntil(c *websocket.Conn, want string) S2C {
	c.SetReadDeadline(time.Now().Add(5 * time.Second))
	for {
		var m S2C
		if err := c.ReadJSON(&m); err != nil {
			log.Fatal("recv: ", err)
		}
		if m.Type == want {
			return m
		}
	}
}

func main() {
	h, st := dialJoin("", "w1", "吊A")
	room := st.Room
	conns := []*websocket.Conn{h}
	for i := 2; i <= 4; i++ {
		c, _ := dialJoin(room, fmt.Sprintf("w%d", i), fmt.Sprintf("吊%s", string(rune('A'+i-1))))
		conns = append(conns, c)
	}
	fmt.Println("4人建房 OK:", room)

	conns[1].WriteJSON(map[string]any{"type": "wind"})
	m := readUntil(h, "state")

	valid := map[string]bool{"东风": true, "南风": true, "西风": true, "北风": true}
	got := map[string]string{}
	for _, p := range m.Players {
		id := p["id"].(string)
		w, _ := p["wind"].(string)
		if !valid[w] {
			log.Fatalf("风位非法: %s=%q", id, w)
		}
		if old, dup := got[w]; dup {
			log.Fatalf("风位重复: %s 与 %s 都是 %s", old, id, w)
		}
		got[w] = id
	}
	if len(got) != 4 {
		log.Fatalf("风位数量不对: %d", len(got))
	}
	for _, p := range m.Players {
		fmt.Printf("  %v = %v\n", p["name"], p["wind"])
	}
	fmt.Println("吊风 OK: 四人各得一个不重复的风位")

	// 再吊一次，仍然合法且不重复
	conns[0].WriteJSON(map[string]any{"type": "wind"})
	m = readUntil(h, "state")
	got2 := map[string]bool{}
	for _, p := range m.Players {
		w, _ := p["wind"].(string)
		if !valid[w] || got2[w] {
			log.Fatalf("二次吊风异常: %v", m.Players)
		}
		got2[w] = true
	}
	fmt.Println("二次吊风 OK: 重新分配，仍不重复")

	// 记分不受吊风影响
	h.WriteJSON(map[string]any{"type": "play", "winner": "w1", "kind": "zimo"})
	m = readUntil(h, "round")
	if m.Balances["w1"] != 30 || m.Balances["w2"] != -10 {
		log.Fatalf("吊风后记分异常: %v", m.Balances)
	}
	fmt.Println("吊风后记分 OK: w1=+30，其余三家各 -10")
	fmt.Println("✅ 全部通过")
}
