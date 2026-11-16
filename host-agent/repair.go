package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os/exec"
	"strings"
)

type repairStep struct {
	RepairID string   `json:"repairId"`
	Seq      int      `json:"seq"`
	Argv     []string `json:"argv"`
}

func pullRepairStep(controlPlane, hostID string) (*repairStep, error) {
	endpoint := strings.TrimRight(controlPlane, "/") + "/api/hosts/" + hostID + "/repair-step"
	req, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	payload, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("repair step: %s: %s", resp.Status, bytes.TrimSpace(payload))
	}
	var env struct {
		Success bool            `json:"success"`
		Data    json.RawMessage `json:"data"`
	}
	if err := json.Unmarshal(payload, &env); err != nil {
		return nil, err
	}
	if !env.Success {
		return nil, fmt.Errorf("repair step: control plane rejected the pull")
	}
	if len(env.Data) == 0 || string(env.Data) == "null" {
		return nil, nil
	}
	var step repairStep
	if err := json.Unmarshal(env.Data, &step); err != nil {
		return nil, err
	}
	return &step, nil
}

func runArgv(argv []string) (int, string) {
	if len(argv) == 0 {
		return 127, "empty command"
	}
	out, err := exec.Command(argv[0], argv[1:]...).CombinedOutput()
	text := string(out)
	if len(text) > 8192 {
		text = text[:8192]
	}
	if err == nil {
		return 0, text
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		return exitErr.ExitCode(), text
	}
	if text == "" {
		text = err.Error()
	}
	if len(text) > 8192 {
		text = text[:8192]
	}
	return 127, text
}

func postRepairResult(controlPlane, hostID string, step repairStep, exitCode int, output string) error {
	body, err := json.Marshal(map[string]any{
		"repairId": step.RepairID,
		"seq":      step.Seq,
		"exitCode": exitCode,
		"output":   output,
	})
	if err != nil {
		return err
	}
	endpoint := strings.TrimRight(controlPlane, "/") + "/api/hosts/" + hostID + "/repair-results"
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
		return fmt.Errorf("repair result: %s: %s", resp.Status, bytes.TrimSpace(msg))
	}
	return nil
}

// executeOneRepair runs the single step the control plane has issued, then stops.
// It does not ask for another step.
func executeOneRepair(controlPlane, hostID string) error {
	step, err := pullRepairStep(controlPlane, hostID)
	if err != nil || step == nil {
		return err
	}
	exitCode, output := runArgv(step.Argv)
	return postRepairResult(controlPlane, hostID, *step, exitCode, output)
}
