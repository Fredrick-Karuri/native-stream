// config/config.go — NS-150
// Loads server configuration from ~/.config/nativestream/config.yaml
// Uses stdlib only — no third-party dependencies.

package config

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Server    ServerConfig
	Store     StoreConfig
	Probe     ProbeConfig
	EPG       EPGConfig
	Proxy     ProxyConfig
	Discovery DiscoveryConfig
	Seed      SeedConfig
}

type ServerConfig struct {
	Host string
	Port int
}

func (s ServerConfig) Addr() string {
	return fmt.Sprintf("%s:%d", s.Host, s.Port)
}

type StoreConfig struct {
	SnapshotPath     string
	SnapshotInterval time.Duration
}

type ProbeConfig struct {
	Interval        time.Duration
	Timeout         time.Duration
	Concurrency     int
	MinScoreActive  float64
	MinScorePromote float64
}

type EPGConfig struct {
	Enabled         bool
	RefreshInterval time.Duration
	LookaheadHours  int
	CachePath       string
	ESPNEnabled     bool
	FootballDataKey string
}

type ProxyConfig struct {
	Enabled   bool
	Referer   string
	UserAgent string
}

type DiscoveryConfig struct {
	Enabled          bool
	DefaultInterval  time.Duration
	PriorityInterval time.Duration
}

type SeedConfig struct {
	M3UPath string
}

func Defaults() Config {
	home, _ := os.UserHomeDir()
	base := filepath.Join(home, ".config", "nativestream")
	return Config{
		Server: ServerConfig{Host: "127.0.0.1", Port: 8888},
		Store: StoreConfig{
			SnapshotPath:     filepath.Join(base, "channels.json"),
			SnapshotInterval: 5 * time.Minute,
		},
		Probe: ProbeConfig{
			Interval:        10 * time.Minute,
			Timeout:         5 * time.Second,
			Concurrency:     20,
			MinScoreActive:  0.3,
			MinScorePromote: 0.5,
		},
		EPG: EPGConfig{
			Enabled:         true,
			RefreshInterval: 6 * time.Hour,
			LookaheadHours:  48,
			CachePath:       filepath.Join(base, "epg_cache.xml"),
			ESPNEnabled:     true,
		},
		Proxy: ProxyConfig{
			UserAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
		},
		Discovery: DiscoveryConfig{
			DefaultInterval:  30 * time.Minute,
			PriorityInterval: 5 * time.Minute,
		},
	}
}

func Load() (Config, error) {
	cfg := Defaults()
	home, _ := os.UserHomeDir()
	path := filepath.Join(home, ".config", "nativestream", "config.yaml")

	f, err := os.Open(path)
	if os.IsNotExist(err) {
		return cfg, nil
	}
	if err != nil {
		return cfg, fmt.Errorf("open config: %w", err)
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		parts := strings.SplitN(line, ":", 2)
		if len(parts) != 2 {
			continue
		}
		key := strings.TrimSpace(parts[0])
		val := strings.TrimSpace(parts[1])
		applyKey(&cfg, key, val)
	}

	cfg.Store.SnapshotPath = expandHome(cfg.Store.SnapshotPath)
	cfg.EPG.CachePath = expandHome(cfg.EPG.CachePath)
	cfg.Seed.M3UPath = expandHome(cfg.Seed.M3UPath)
	return cfg, scanner.Err()
}

func applyKey(cfg *Config, key, val string) {
	switch key {
	case "host":
		cfg.Server.Host = val
	case "port":
		if n, err := strconv.Atoi(val); err == nil {
			cfg.Server.Port = n
		}
	case "snapshot_path":
		cfg.Store.SnapshotPath = val
	case "snapshot_interval":
		if d, err := time.ParseDuration(val); err == nil {
			cfg.Store.SnapshotInterval = d
		}
	case "probe_interval":
		if d, err := time.ParseDuration(val); err == nil {
			cfg.Probe.Interval = d
		}
	case "probe_timeout":
		if d, err := time.ParseDuration(val); err == nil {
			cfg.Probe.Timeout = d
		}
	case "probe_concurrency":
		if n, err := strconv.Atoi(val); err == nil {
			cfg.Probe.Concurrency = n
		}
	case "epg_enabled":
		cfg.EPG.Enabled = val == "true"
	case "epg_refresh_interval":
		if d, err := time.ParseDuration(val); err == nil {
			cfg.EPG.RefreshInterval = d
		}
	case "espn_enabled":
		cfg.EPG.ESPNEnabled = val == "true"
	case "football_data_key":
		cfg.EPG.FootballDataKey = val
	case "proxy_enabled":
		cfg.Proxy.Enabled = val == "true"
	case "proxy_referer":
		cfg.Proxy.Referer = val
	case "proxy_user_agent":
		cfg.Proxy.UserAgent = val
	case "discovery_enabled":
		cfg.Discovery.Enabled = val == "true"
	case "seed_m3u_path":
		cfg.Seed.M3UPath = val
	}
}

func expandHome(path string) string {
	if !strings.HasPrefix(path, "~") {
		return path
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, path[1:])
}