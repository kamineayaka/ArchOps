package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestExecuteOneRepairRunsOnlyTheIssuedArgv(t *testing.T) {
	logPath := filepath.Join(t.TempDir(), "ran")
	script := filepath.Join(t.TempDir(), "yarn")
	body := "#!/bin/sh\nprintf '%s\\n' \"$*\" >> " + shellQuote(logPath) + "\necho repair-output\nexit 1\n"
	if err := os.WriteFile(script, []byte(body), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(script, 0o755); err != nil {
		t.Fatal(err)
	}
	var gets int
	var posted []byte
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-ArchOps-User-Id") != "" || r.Header.Get("Authorization") != "" {
			t.Errorf("identity headers %v", r.Header)
		}
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/api/hosts/host-1/repair-step":
			gets++
			if r.Method != http.MethodGet {
				t.Errorf("method %s", r.Method)
			}
			fmtJSON(t, w, map[string]any{
				"success": true,
				"code":    "OK",
				"message": "ok",
				"data": map[string]any{
					"repairId": "repair-1",
					"seq":      1,
					"argv":     []string{script, "--daemon", "start", "nodemanager"},
				},
			})
		case "/api/hosts/host-1/repair-results":
			if r.Method != http.MethodPost {
				t.Errorf("method %s", r.Method)
			}
			var err error
			posted, err = io.ReadAll(r.Body)
			if err != nil {
				t.Fatal(err)
			}
			fmtJSON(t, w, map[string]any{
				"success": true,
				"code":    "OK",
				"message": "ok",
				"data":    nil,
			})
		default:
			t.Errorf("path %s", r.URL.Path)
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	if err := executeOneRepair(server.URL, "host-1"); err != nil {
		t.Fatal(err)
	}
	if gets != 1 {
		t.Fatalf("pulls %d", gets)
	}
	ran, err := os.ReadFile(logPath)
	if err != nil {
		t.Fatal(err)
	}
	if string(ran) != "--daemon start nodemanager\n" {
		t.Fatalf("ran %q", ran)
	}
	var result map[string]any
	if err := json.Unmarshal(posted, &result); err != nil {
		t.Fatal(err)
	}
	if result["repairId"] != "repair-1" || result["seq"] != float64(1) || result["exitCode"] != float64(1) {
		t.Fatalf("result %#v", result)
	}
	if result["output"] != "repair-output\n" {
		t.Fatalf("output %#v", result["output"])
	}
	if _, ok := result["cause"]; ok {
		t.Fatalf("result %#v", result)
	}
}

func TestExecuteOneRepairSkipsWhenNothingIsIssued(t *testing.T) {
	posts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/hosts/host-1/repair-step" {
			posts++
			t.Errorf("path %s", r.URL.Path)
		}
		fmtJSON(t, w, map[string]any{
			"success": true,
			"code":    "OK",
			"message": "ok",
			"data":    nil,
		})
	}))
	defer server.Close()
	if err := executeOneRepair(server.URL, "host-1"); err != nil {
		t.Fatal(err)
	}
	if posts != 0 {
		t.Fatalf("posts %d", posts)
	}
}

func fmtJSON(t *testing.T, w http.ResponseWriter, payload any) {
	t.Helper()
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		t.Errorf("encode: %v", err)
	}
}

func shellQuote(path string) string {
	return "'" + path + "'"
}
