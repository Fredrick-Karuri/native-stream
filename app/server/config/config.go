// config/config.go — NS-150
// Loads server configuration from ~/.config/nativestream/config.yaml
// Uses stdlib only — no third-party dependencies.

package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
	"gopkg.in/yaml.v3"
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
	MinScoreHealthy float64
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
    GistIDs          []string
    GistToken        string
    Subreddits       []string
    TelegramChannels []string
    DirectM3UURLs    []string
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
			MinScoreHealthy: 0.3,
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

	var raw struct {
		Server struct {
			Host string `yaml:"host"`
			Port int    `yaml:"port"`
		} `yaml:"server"`
		Store struct {
			SnapshotPath     string `yaml:"snapshot_path"`
			SnapshotInterval string `yaml:"snapshot_interval"`
			MinScoreHealthy  float64 `yaml:"min_score_healthy"`
		} `yaml:"store"`
		Probe struct {
			Interval    string `yaml:"interval"`
			Timeout     string `yaml:"timeout"`
			Concurrency int    `yaml:"concurrency"`
			MinScorePromote float64 `yaml:"min_score_promote"`
			MinScoreActive  float64 `yaml:"min_score_active"`
		} `yaml:"probe"`
		EPG struct {
			Enabled         bool   `yaml:"enabled"`
			RefreshInterval string `yaml:"refresh_interval"`
			LookaheadHours  int    `yaml:"lookahead_hours"`
			CachePath       string `yaml:"cache_path"`
			Sources         struct {
				ESPN         struct{ Enabled bool `yaml:"enabled"` } `yaml:"espn"`
				FootballData struct {
					Enabled bool   `yaml:"enabled"`
					APIKey  string `yaml:"api_key"`
				} `yaml:"football_data"`
			} `yaml:"sources"`
		} `yaml:"epg"`
		Proxy struct {
			Enabled   bool   `yaml:"enabled"`
			Referer   string `yaml:"referer"`
			UserAgent string `yaml:"user_agent"`
		} `yaml:"proxy"`

		Discovery struct {
			Enabled          bool     `yaml:"enabled"`
			DefaultInterval  string   `yaml:"default_interval"`
			PriorityInterval string   `yaml:"priority_interval"`
			Gists struct {
				Enabled  bool     `yaml:"enabled"`
				Token    string   `yaml:"token"`
				GistIDs  []string `yaml:"gist_ids"`
			} `yaml:"gists"`
			Reddit struct {
				Enabled     bool     `yaml:"enabled"`
				Subreddits  []string `yaml:"subreddits"`
			} `yaml:"reddit"`
			Telegram struct {
				Enabled  bool     `yaml:"enabled"`
				Channels []string `yaml:"channels"`
			} `yaml:"telegram"`
			DirectM3U struct {
				Enabled bool     `yaml:"enabled"`
				URLs    []string `yaml:"urls"`
			} `yaml:"direct_m3u"`
		} `yaml:"discovery"`

		Seed struct {
			M3UPath string `yaml:"m3u_path"`
		} `yaml:"seed"`
	}

	if err := yaml.NewDecoder(f).Decode(&raw); err != nil {
		return cfg, fmt.Errorf("parse config: %w", err)
	}

	if raw.Server.Host != "" { cfg.Server.Host = raw.Server.Host }
	if raw.Server.Port != 0  { cfg.Server.Port = raw.Server.Port }

	if raw.Store.SnapshotPath != ""     { cfg.Store.SnapshotPath = expandHome(raw.Store.SnapshotPath) }
	if d, err := time.ParseDuration(raw.Store.SnapshotInterval); err == nil { cfg.Store.SnapshotInterval = d }

	if raw.Probe.Concurrency != 0 { cfg.Probe.Concurrency = raw.Probe.Concurrency }
	if d, err := time.ParseDuration(raw.Probe.Interval); err == nil { cfg.Probe.Interval = d }
	if d, err := time.ParseDuration(raw.Probe.Timeout);  err == nil { cfg.Probe.Timeout = d }

	cfg.EPG.Enabled = raw.EPG.Enabled
	cfg.EPG.ESPNEnabled = raw.EPG.Sources.ESPN.Enabled
	cfg.EPG.FootballDataKey = raw.EPG.Sources.FootballData.APIKey
	if raw.EPG.CachePath != ""    { cfg.EPG.CachePath = expandHome(raw.EPG.CachePath) }
	if raw.EPG.LookaheadHours != 0 { cfg.EPG.LookaheadHours = raw.EPG.LookaheadHours }
	if d, err := time.ParseDuration(raw.EPG.RefreshInterval); err == nil { cfg.EPG.RefreshInterval = d }

	cfg.Proxy.Enabled = raw.Proxy.Enabled
	if raw.Proxy.Referer   != "" { cfg.Proxy.Referer = raw.Proxy.Referer }
	if raw.Proxy.UserAgent != "" { cfg.Proxy.UserAgent = raw.Proxy.UserAgent }

	cfg.Discovery.Enabled = raw.Discovery.Enabled
	if d, err := time.ParseDuration(raw.Discovery.DefaultInterval);  err == nil { cfg.Discovery.DefaultInterval = d }
	if d, err := time.ParseDuration(raw.Discovery.PriorityInterval); err == nil { cfg.Discovery.PriorityInterval = d }

	if raw.Discovery.Gists.Enabled     { cfg.Discovery.GistIDs = raw.Discovery.Gists.GistIDs; cfg.Discovery.GistToken = raw.Discovery.Gists.Token }
	if raw.Discovery.Reddit.Enabled    { cfg.Discovery.Subreddits = raw.Discovery.Reddit.Subreddits }
	if raw.Discovery.Telegram.Enabled  { cfg.Discovery.TelegramChannels = raw.Discovery.Telegram.Channels }
	if raw.Discovery.DirectM3U.Enabled { cfg.Discovery.DirectM3UURLs = raw.Discovery.DirectM3U.URLs }


	if raw.Seed.M3UPath != "" { cfg.Seed.M3UPath = expandHome(raw.Seed.M3UPath) }

	if raw.Probe.MinScorePromote != 0 { cfg.Probe.MinScorePromote = raw.Probe.MinScorePromote }
	if raw.Store.MinScoreHealthy != 0 { cfg.Store.MinScoreHealthy = raw.Store.MinScoreHealthy }

	return cfg, nil
}


func expandHome(path string) string {
	if !strings.HasPrefix(path, "~") {
		return path
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, path[1:])
}