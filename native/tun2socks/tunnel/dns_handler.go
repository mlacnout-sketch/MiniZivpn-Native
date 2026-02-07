package tunnel

import (
	"fmt"
	"net"
	"time"

	"github.com/miekg/dns"
	"github.com/xjasonlyu/tun2socks/v2/core/adapter"
	"github.com/xjasonlyu/tun2socks/v2/log"
)

// handleDNS handles UDP DNS queries by resolving them via TCP.
func (t *Tunnel) handleDNS(uc adapter.UDPConn) {
	defer uc.Close()

	buf := make([]byte, 2048)
	n, addr, err := uc.ReadFrom(buf)
	if err != nil {
		return
	}

	msg := new(dns.Msg)
	if err := msg.Unpack(buf[:n]); err != nil {
		log.Warnf("[DNS] Failed to unpack DNS query: %v", err)
		return
	}

	if len(msg.Question) == 0 {
		return
	}

	domain := msg.Question[0].Name
	log.Infof("[DNS] Query: %s via TCP", domain)

	// Resolve via TCP to 8.8.8.8:53
	resp, err := t.resolveDNSTCP(msg)
	if err != nil {
		log.Warnf("[DNS] Failed to resolve %s via TCP: %v", domain, err)
		return
	}

	out, err := resp.Pack()
	if err != nil {
		return
	}

	uc.WriteTo(out, addr)
}

func (t *Tunnel) resolveDNSTCP(query *dns.Msg) (*dns.Msg, error) {
	client := &dns.Client{
		Net:     "tcp",
		Timeout: 5 * time.Second,
	}

	// We use 8.8.8.8:53 as the reliable upstream DNS
	resp, _, err := client.Exchange(query, "8.8.8.8:53")
	if err != nil {
		return nil, err
	}

	if resp == nil {
		return nil, fmt.Errorf("empty response")
	}

	return resp, nil
}
