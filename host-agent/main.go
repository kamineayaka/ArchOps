package main

import (
	"flag"
	"fmt"
	"os"
)

func main() {
	controlPlane := flag.String("control-plane", "http://127.0.0.1:8081", "control plane base URL")
	hostID := flag.String("host-id", "", "host id")
	procRoot := flag.String("proc-root", "/proc", "proc filesystem to read")
	var roles []string
	flag.Func("role", "role placed on this host; repeat for more than one", func(value string) error {
		roles = append(roles, value)
		return nil
	})
	flag.Parse()
	if *hostID == "" {
		fmt.Fprintln(os.Stderr, "--host-id is required")
		os.Exit(2)
	}
	body, err := reportBody(*procRoot, roles)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	if err := postReport(*controlPlane, *hostID, body); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
