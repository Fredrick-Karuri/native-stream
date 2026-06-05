// config/config.go
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

// ── Domain types ─────────────────────────────────────────────────────────────

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
	MinScoreHealthy  float64
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
	Origin    string
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
	LocalScriptPath  string
}

type SeedConfig struct {
	M3UPath string
}

// ── Raw YAML shape ────────────────────────────────────────────────────────────

type rawConfig struct {
	Server struct {
		Host string `yaml:"host"`
		Port int    `yaml:"port"`
	} `yaml:"server"`
	Store struct {
		SnapshotPath     string  `yaml:"snapshot_path"`
		SnapshotInterval string  `yaml:"snapshot_interval"`
		MinScoreHealthy  float64 `yaml:"min_score_healthy"`
	} `yaml:"store"`
	Probe struct {
		Interval        string  `yaml:"interval"`
		Timeout         string  `yaml:"timeout"`
		Concurrency     int     `yaml:"concurrency"`
		MinScoreActive  float64 `yaml:"min_score_active"`
		MinScorePromote float64 `yaml:"min_score_promote"`
	} `yaml:"probe"`
	EPG struct {
		Enabled         bool   `yaml:"enabled"`
		RefreshInterval string `yaml:"refresh_interval"`
		LookaheadHours  int    `yaml:"lookahead_hours"`
		CachePath       string `yaml:"cache_path"`
		Sources         struct {
			ESPN struct {
				Enabled bool `yaml:"enabled"`
			} `yaml:"espn"`
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
		Origin    string `yaml:"origin"`
	} `yaml:"proxy"`
	Discovery struct {
		Enabled          bool   `yaml:"enabled"`
		DefaultInterval  string `yaml:"default_interval"`
		PriorityInterval string `yaml:"priority_interval"`
		Gists            struct {
			Enabled bool     `yaml:"enabled"`
			Token   string   `yaml:"token"`
			GistIDs []string `yaml:"gist_ids"`
		} `yaml:"gists"`
		Reddit struct {
			Enabled    bool     `yaml:"enabled"`
			Subreddits []string `yaml:"subreddits"`
		} `yaml:"reddit"`
		Telegram struct {
			Enabled  bool     `yaml:"enabled"`
			Channels []string `yaml:"channels"`
		} `yaml:"telegram"`
		DirectM3U struct {
			Enabled bool     `yaml:"enabled"`
			URLs    []string `yaml:"urls"`
		} `yaml:"direct_m3u"`
		LocalScript struct {
			Enabled bool   `yaml:"enabled"`
			Path    string `yaml:"path"`
		} `yaml:"local_script"`
	} `yaml:"discovery"`
	Seed struct {
		M3UPath string `yaml:"m3u_path"`
	} `yaml:"seed"`
}

// ── Defaults ──────────────────────────────────────────────────────────────────

func Defaults() Config {
	home, _ := os.UserHomeDir()
	base := filepath.Join(home, ".config", "nativestream")
	return Config{
		Server: ServerConfig{Host: "127.0.0.1", Port: 8888},
		Store: StoreConfig{
			SnapshotPath:     filepath.Join(base, "channels.json"),
			SnapshotInterval: 5 * time.Minute,
			MinScoreHealthy:  0.3,
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

// ── Entry point ───────────────────────────────────────────────────────────────

// Load resolves the config path, applies Docker env overrides, then delegates
// to loadFile.
func Load() (Config, error) {
	cfg := Defaults()

	// Docker: explicit config path overrides everything.
	if p := os.Getenv("NATIVESTREAM_CONFIG"); p != "" {
		return loadFile(cfg, p)
	}
	// Docker: data directory for snapshots/cache.
	if d := os.Getenv("NATIVESTREAM_DATA"); d != "" {
		cfg.Store.SnapshotPath = filepath.Join(d, "channels.json")
		cfg.EPG.CachePath = filepath.Join(d, "epg_cache.xml")
	}
	// Docker: bind all interfaces.
	if os.Getenv("NATIVESTREAM_DOCKER") == "1" {
		cfg.Server.Host = "0.0.0.0"
	}

	home, _ := os.UserHomeDir()
	return loadFile(cfg, filepath.Join(home, ".config", "nativestream", "config.yaml"))
}

// loadFile opens path, decodes YAML into rawConfig, and merges it into cfg.
func loadFile(cfg Config, path string) (Config, error) {
	f, err := os.Open(path)
	if os.IsNotExist(err) {
		return cfg, nil
	}
	if err != nil {
		return cfg, fmt.Errorf("open config: %w", err)
	}
	defer f.Close()

	var raw rawConfig
	if err := yaml.NewDecoder(f).Decode(&raw); err != nil {
		return cfg, fmt.Errorf("parse config: %w", err)
	}

	applyRaw(&cfg, &raw)
	return cfg, nil
}

// ── Section mergers ───────────────────────────────────────────────────────────

func applyRaw(cfg *Config, raw *rawConfig) {
	applyServer(cfg, raw)
	applyStore(cfg, raw)
	applyProbe(cfg, raw)
	applyEPG(cfg, raw)
	applyProxy(cfg, raw)
	applyDiscovery(cfg, raw)
	applySeed(cfg, raw)
}

func applyServer(cfg *Config, raw *rawConfig) {
	applyString(&cfg.Server.Host, raw.Server.Host)
	applyInt(&cfg.Server.Port, raw.Server.Port)
}

func applyStore(cfg *Config, raw *rawConfig) {
	applyString(&cfg.Store.SnapshotPath, expandHome(raw.Store.SnapshotPath))
	applyDuration(&cfg.Store.SnapshotInterval, raw.Store.SnapshotInterval)
	applyFloat(&cfg.Store.MinScoreHealthy, raw.Store.MinScoreHealthy)
}

func applyProbe(cfg *Config, raw *rawConfig) {
	applyDuration(&cfg.Probe.Interval, raw.Probe.Interval)
	applyDuration(&cfg.Probe.Timeout, raw.Probe.Timeout)
	applyInt(&cfg.Probe.Concurrency, raw.Probe.Concurrency)
	applyFloat(&cfg.Probe.MinScoreActive, raw.Probe.MinScoreActive)
	applyFloat(&cfg.Probe.MinScorePromote, raw.Probe.MinScorePromote)
}

func applyEPG(cfg *Config, raw *rawConfig) {
	cfg.EPG.Enabled = raw.EPG.Enabled
	cfg.EPG.ESPNEnabled = raw.EPG.Sources.ESPN.Enabled
	cfg.EPG.FootballDataKey = raw.EPG.Sources.FootballData.APIKey
	applyString(&cfg.EPG.CachePath, expandHome(raw.EPG.CachePath))
	applyInt(&cfg.EPG.LookaheadHours, raw.EPG.LookaheadHours)
	applyDuration(&cfg.EPG.RefreshInterval, raw.EPG.RefreshInterval)
}

func applyProxy(cfg *Config, raw *rawConfig) {
	cfg.Proxy.Enabled = raw.Proxy.Enabled
	applyString(&cfg.Proxy.Referer, raw.Proxy.Referer)
	applyString(&cfg.Proxy.UserAgent, raw.Proxy.UserAgent)
	applyString(&cfg.Proxy.Origin, raw.Proxy.Origin)
}

func applyDiscovery(cfg *Config, raw *rawConfig) {
	cfg.Discovery.Enabled = raw.Discovery.Enabled
	applyDuration(&cfg.Discovery.DefaultInterval, raw.Discovery.DefaultInterval)
	applyDuration(&cfg.Discovery.PriorityInterval, raw.Discovery.PriorityInterval)

	if raw.Discovery.Gists.Enabled {
		cfg.Discovery.GistIDs = raw.Discovery.Gists.GistIDs
		cfg.Discovery.GistToken = raw.Discovery.Gists.Token
	}
	if raw.Discovery.Reddit.Enabled {
		cfg.Discovery.Subreddits = raw.Discovery.Reddit.Subreddits
	}
	if raw.Discovery.Telegram.Enabled {
		cfg.Discovery.TelegramChannels = raw.Discovery.Telegram.Channels
	}
	if raw.Discovery.DirectM3U.Enabled {
		cfg.Discovery.DirectM3UURLs = raw.Discovery.DirectM3U.URLs
	}
	if raw.Discovery.LocalScript.Enabled {
    	cfg.Discovery.LocalScriptPath = raw.Discovery.LocalScript.Path
	}
}

func applySeed(cfg *Config, raw *rawConfig) {
	applyString(&cfg.Seed.M3UPath, expandHome(raw.Seed.M3UPath))
}

// ── Merge helpers ─────────────────────────────────────────────────────────────

func applyString(dst *string, src string) {
	if src != "" {
		*dst = src
	}
}

func applyInt(dst *int, src int) {
	if src != 0 {
		*dst = src
	}
}

func applyFloat(dst *float64, src float64) {
	if src != 0 {
		*dst = src
	}
}

func applyDuration(dst *time.Duration, src string) {
	if d, err := time.ParseDuration(src); err == nil {
		*dst = d
	}
}

// ── Utilities ─────────────────────────────────────────────────────────────────

func expandHome(path string) string {
	if !strings.HasPrefix(path, "~") {
		return path
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, path[1:])
}