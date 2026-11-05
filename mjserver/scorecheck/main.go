// 新计分规则测试：点炮单付20、自摸三家各10、宝中宝40、撤局回滚、点炮由赢家发起应被拒
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
	Host     string           `json:"host"`
	Players  []map[string]any `json:"players"`
	Rounds   []map[string]any `json:"rounds"`
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
	h, st := dialJoin("", "p1", "房主")
	room := st.Room
	var others []*websocket.Conn
	for i := 2; i <= 4; i++ {
		c, _ := dialJoin(room, fmt.Sprintf("p%d", i), fmt.Sprintf("玩家%d", i))
		others = append(others, c)
	}
	fmt.Println("4人建房 OK:", room)

	// 1. 点炮：p2 点 p1，p2 单付 20
	others[0].WriteJSON(map[string]any{"type": "play", "winner": "p1", "kind": "dianpao"})
	m := readUntil(h, "round")
	if m.Balances["p1"] != 20 || m.Balances["p2"] != -20 {
		log.Fatalf("点炮结算错误: %v", m.Balances)
	}
	fmt.Println("点炮 OK: p1=+20, p2=-20（其余人不变）")

	// 2. 点炮由赢家本人发起应被拒
	h.WriteJSON(map[string]any{"type": "play", "winner": "p1", "kind": "dianpao"})
	m = readUntil(h, "error")
	fmt.Println("赢家发起点炮被拒 OK:", m.Msg)

	// 3. 自摸：p1 自摸，其余三家各付 10
	h.WriteJSON(map[string]any{"type": "play", "winner": "p1", "kind": "zimo"})
	m = readUntil(h, "round")
	if m.Balances["p1"] != 50 || m.Balances["p2"] != -30 || m.Balances["p3"] != -10 || m.Balances["p4"] != -10 {
		log.Fatalf("自摸结算错误: %v", m.Balances)
	}
	fmt.Println("自摸 OK: p1=+50, p2/p3/p4 各 -10")

	// 4. 宝中宝：p3 赢，每家 40
	others[1].WriteJSON(map[string]any{"type": "play", "winner": "p3", "kind": "baozhongbao"})
	m = readUntil(h, "round")
	if m.Balances["p3"] != 110 || m.Balances["p1"] != 10 {
		log.Fatalf("宝中宝结算错误: %v", m.Balances)
	}
	fmt.Println("宝中宝 OK: p3=+110, p1=+10")

	// 5. 撤局回滚（房主撤最近一局）：应回到宝中宝之前的状态
	h.WriteJSON(map[string]any{"type": "undo", "seq": 3})
	m = readUntil(h, "undid")
	if m.Balances["p1"] != 50 || m.Balances["p2"] != -30 || m.Balances["p3"] != -10 || m.Balances["p4"] != -10 {
		log.Fatalf("撤局后结算错误: %v", m.Balances)
	}
	fmt.Println("撤局回滚 OK: 恢复为 p1=+50, p2=-30, p3=-10, p4=-10")

	fmt.Println("✅ 全部通过")
}
