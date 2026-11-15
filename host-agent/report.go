package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

type roleLiveness struct {
	Role string `json:"role"`
	Up   bool   `json:"up"`
	Raw  string `json:"raw"`
}

func cmdlines(procRoot string) ([]string, error) {
	entries, err := os.ReadDir(procRoot)
	if err != nil {
		return nil, err
	}
	var pids []string
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		if _, err := strconv.Atoi(entry.Name()); err != nil {
			continue
		}
		pids = append(pids, entry.Name())
	}
	sort.Slice(pids, func(i, j int) bool {
		left, _ := strconv.Atoi(pids[i])
		right, _ := strconv.Atoi(pids[j])
		return left < right
	})
	var lines []string
	for _, pid := range pids {
		data, err := os.ReadFile(filepath.Join(procRoot, pid, "cmdline"))
		if err != nil {
			continue
		}
		text := strings.TrimRight(string(data), "\x00")
		if text == "" {
			continue
		}
		lines = append(lines, strings.Join(strings.Split(text, "\x00"), " "))
	}
	return lines, nil
}

func collect(roles, processes []string) []roleLiveness {
	raw := strings.Join(processes, "\n")
	out := make([]roleLiveness, 0, len(roles))
	for _, role := range roles {
		up := false
		for _, line := range processes {
			if strings.Contains(line, role) {
				up = true
				break
			}
		}
		out = append(out, roleLiveness{Role: role, Up: up, Raw: raw})
	}
	return out
}

func reportBody(procRoot string, roles []string) ([]byte, error) {
	if len(roles) == 0 {
		return []byte("{}"), nil
	}
	processes, err := cmdlines(procRoot)
	if err != nil {
		return nil, err
	}
	payload := struct {
		RoleLiveness []roleLiveness `json:"roleLiveness"`
	}{RoleLiveness: collect(roles, processes)}
	return json.Marshal(payload)
}

func postReport(controlPlane, hostID string, body []byte) error {
	endpoint := strings.TrimRight(controlPlane, "/") + "/api/hosts/" + hostID + "/role-liveness"
	req, err := http.NewRequest(http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		msg, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("role liveness report: %s: %s", resp.Status, bytes.TrimSpace(msg))
	}
	return nil
}
