PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS targets (
    id INTEGER PRIMARY KEY CHECK (id = 1),

    calories_target REAL NOT NULL,
    protein_target_g REAL NOT NULL,
    fat_target_g REAL NOT NULL,
    net_carbs_target_g REAL NOT NULL,

    water_target_ml REAL DEFAULT 0,
    sodium_target_mg REAL DEFAULT 0,
    potassium_target_mg REAL DEFAULT 0,
    magnesium_target_mg REAL DEFAULT 0,

    expected_events_per_day REAL NOT NULL DEFAULT 4,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    event_timestamp TEXT NOT NULL,
    event_date TEXT NOT NULL,
    event_type TEXT NOT NULL CHECK (
        event_type IN ('meal', 'drink', 'exercise', 'supplement', 'custom')
    ),
    label TEXT NOT NULL,

    calories REAL NOT NULL DEFAULT 0,
    protein_g REAL NOT NULL DEFAULT 0,
    fat_g REAL NOT NULL DEFAULT 0,
    net_carbs_g REAL NOT NULL DEFAULT 0,

    water_ml REAL NOT NULL DEFAULT 0,
    sodium_mg REAL NOT NULL DEFAULT 0,
    potassium_mg REAL NOT NULL DEFAULT 0,
    magnesium_mg REAL NOT NULL DEFAULT 0,

    source TEXT NOT NULL DEFAULT 'manual',
    source_id TEXT,
    notes TEXT,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_events_event_date
    ON events (event_date);

CREATE INDEX IF NOT EXISTS idx_events_event_timestamp
    ON events (event_timestamp);

CREATE INDEX IF NOT EXISTS idx_events_event_type
    ON events (event_type);
