package common

import "hivejam/config"

import "log"
import "net"

import "github.com/scgolang/osc"

func Connect(address string, port int) *osc.UDPConn {
	ip := net.ParseIP(address)
	if ip == nil {
		panic("Unable to parse IP.")
	}
	addr := &net.UDPAddr{ip, port, ""}
	conn, err := osc.DialUDP("udp", nil, addr)
	if err != nil {
		panic(err)
	}
	return conn
}

func Listen(address string, port int) *osc.UDPConn {
	ip := net.ParseIP(address)
	if ip == nil {
		panic("Unable to parse IP.")
	}
	addr := &net.UDPAddr{ip, port, ""}
	conn, err := osc.ListenUDP("udp", addr)
	if err != nil {
		panic(err)
	}
	return conn
}

func LogError(v interface{}) {
	log.Print(v)
}

func LogInfo(v interface{}) {
	if *config.Flags.Verbose || *config.Flags.Debug {
		log.Print(v)
	}
}

func LogDebug(v interface{}) {
	if *config.Flags.Debug {
		log.Print(v)
	}
}
