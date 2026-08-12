import sqlite3
from datetime import date, datetime, timedelta

DB_NAME = 'ankers.db'


def get_db():
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db()
    cursor = conn.cursor()

    cursor.execute('''
        CREATE TABLE IF NOT EXISTS folders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            position INTEGER DEFAULT 0
        )
    ''')

    cursor.execute('''
        CREATE TABLE IF NOT EXISTS decks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            folder_id INTEGER,
            daily_lessons INTEGER DEFAULT 10,
            daily_reviews INTEGER DEFAULT 50,
            position INTEGER DEFAULT 0,
            FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE CASCADE
        )
    ''')

    cursor.execute('''
        CREATE TABLE IF NOT EXISTS cards (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            deck_id INTEGER,
            word TEXT NOT NULL,
            meaning TEXT NOT NULL,
            status TEXT DEFAULT 'Unseen',
            learning_step INTEGER DEFAULT 0,
            srs_stage INTEGER DEFAULT 0,
            next_review_at TEXT,
            extra_info TEXT DEFAULT '',
            FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE
        )
    ''')

    cursor.execute('''
            CREATE TABLE IF NOT EXISTS study_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_id INTEGER,
                date TEXT,
                lessons INTEGER DEFAULT 0,
                reviews INTEGER DEFAULT 0,
                time_spent INTEGER DEFAULT 0,
                UNIQUE(deck_id, date)
            )
        ''')

    cursor.execute('''
        CREATE TABLE IF NOT EXISTS activity (
            date TEXT,
            deck_id INTEGER,
            lessons INTEGER DEFAULT 0,
            reviews INTEGER DEFAULT 0,
            time_spent INTEGER DEFAULT 0,
            PRIMARY KEY (date, deck_id)
        )
    ''')

    cursor.execute('''
        CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT
        )
    ''')

    conn.commit()
    conn.close()


# --- COUNTERS ---

def get_lesson_count(deck_id):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(id) FROM cards WHERE deck_id = ? AND status IN ('New', 'Unseen', 'Learning')",
                   (deck_id,))
    row = cursor.fetchone()
    conn.close()
    return row[0] if row else 0


def get_lesson_count_by_name(deck_name):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT COUNT(c.id) FROM cards c
        JOIN decks d ON c.deck_id = d.id
        WHERE d.name = ? AND c.status IN ('New', 'Unseen', 'Learning')
    ''', (deck_name,))
    row = cursor.fetchone()
    conn.close()
    return row[0] if row else 0


def get_review_count(deck_id):
    conn = get_db()
    cursor = conn.cursor()
    now_iso = datetime.now().isoformat()
    cursor.execute(
        "SELECT COUNT(id) FROM cards WHERE deck_id = ? AND status = 'Reviewing' AND (next_review_at IS NULL OR next_review_at <= ?)",
        (deck_id, now_iso))
    row = cursor.fetchone()
    conn.close()
    return row[0] if row else 0


def get_review_count_by_name(deck_name):
    conn = get_db()
    cursor = conn.cursor()
    now_iso = datetime.now().isoformat()
    cursor.execute('''
        SELECT COUNT(c.id) FROM cards c
        JOIN decks d ON c.deck_id = d.id
        WHERE d.name = ? AND c.status = 'Reviewing' AND (c.next_review_at IS NULL OR c.next_review_at <= ?)
    ''', (deck_name, now_iso))
    row = cursor.fetchone()
    conn.close()
    return row[0] if row else 0


# --- SRS LOGIC ---

def update_card_srs(card_id: int, rating: int):
    conn = get_db()
    cursor = conn.cursor()

    cursor.execute("SELECT status, learning_step, srs_stage FROM cards WHERE id = ?", (card_id,))
    row = cursor.fetchone()
    if not row:
        conn.close()
        return None

    status = row['status']
    if status == 'Unseen' or status == 'New':
        status = 'Learning'

    step = row['learning_step'] if row['learning_step'] is not None else 0
    srs = row['srs_stage'] if row['srs_stage'] is not None else 0

    now = datetime.now()
    new_status = status
    new_step = step
    new_srs = srs
    next_review = now

    srs_intervals = {
        1: timedelta(hours=4),  # App 1 -> App 2
        2: timedelta(hours=8),  # App 2 -> App 3
        3: timedelta(days=1),  # App 3 -> App 4
        4: timedelta(days=2),  # App 4 -> Guru 1
        5: timedelta(days=7),  # Guru 1 -> Guru 2
        6: timedelta(days=14),  # Guru 2 -> Master
        7: timedelta(days=30),  # Master -> Enlightened
        8: timedelta(days=120)  # Enlightened -> Burned
    }

    if status == 'Learning':
        max_learning_steps = 3

        if rating == 1:  # AGAIN
            new_step = 0
            next_review = now
        elif rating == 2:  # HARD
            new_step = step
            next_review = now
        elif rating == 3:  # GOOD
            new_step = step + 1
            if new_step >= max_learning_steps:
                new_step = 3  # capped at 3 (Graduated)
                new_status = 'Reviewing'
                new_srs = 1
                next_review = now + srs_intervals[new_srs]
            else:
                next_review = now
        elif rating == 4:  # EASY
            new_step = step + 2
            if new_step >= max_learning_steps:
                new_step = 3  # capped at 3 (Graduated)
                new_status = 'Reviewing'
                new_srs = 1
                next_review = now + srs_intervals[new_srs]
            else:
                next_review = now

    elif status == 'Reviewing':
        if rating == 1:
            new_status = 'Learning'
            new_step = 0
            new_srs = 0
            next_review = now
        elif rating == 2:
            next_review = now + timedelta(hours=2)
        elif rating == 3 or rating == 4:
            new_srs = srs + 1 if rating == 3 else srs + 2
            if new_srs >= 9:
                new_status = 'Graduated'
                new_srs = 9
                next_review = now + timedelta(days=3650)
            else:
                next_review = now + srs_intervals.get(new_srs, timedelta(days=120))

    cursor.execute("""
        UPDATE cards 
        SET status = ?, learning_step = ?, srs_stage = ?, next_review_at = ? 
        WHERE id = ?
    """, (new_status, new_step, new_srs, next_review.isoformat(), card_id))

    conn.commit()
    conn.close()

    return {'status': new_status, 'srs_stage': new_srs, 'learning_step': new_step}


# --- DECK ACTIVITY & STREAKS ---

def log_activity(deck_id: int, lessons: int = 0, reviews: int = 0, time_spent: int = 0):
    if not deck_id:
        return
    conn = get_db()
    cursor = conn.cursor()
    today_str = date.today().isoformat()

    cursor.execute("""
        INSERT INTO activity (date, deck_id, lessons, reviews, time_spent)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(date, deck_id) DO UPDATE SET 
            lessons = lessons + ?,
            reviews = reviews + ?,
            time_spent = time_spent + ?
    """, (today_str, deck_id, lessons, reviews, time_spent, lessons, reviews, time_spent))
    conn.commit()
    conn.close()


def get_activity_from_db(d, deck_id):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT lessons, reviews FROM activity WHERE date = ? AND deck_id = ?', (d.isoformat(), deck_id))
    row = cursor.fetchone()
    conn.close()
    if row:
        return {'lessons': row['lessons'], 'reviews': row['reviews']}
    return {'lessons': 0, 'reviews': 0}


def get_month_activity(year, month, deck_id):
    conn = get_db()
    cursor = conn.cursor()
    start_date = date(year, month, 1).isoformat()
    if month == 12:
        end_date = date(year + 1, 1, 1).isoformat()
    else:
        end_date = date(year, month + 1, 1).isoformat()
    cursor.execute(
        'SELECT SUM(lessons) as total_lessons, SUM(reviews) as total_reviews FROM activity WHERE date >= ? AND date < ? AND deck_id = ?',
        (start_date, end_date, deck_id))
    row = cursor.fetchone()
    conn.close()
    return {'lessons': row['total_lessons'] or 0, 'reviews': row['total_reviews'] or 0}


def get_year_activity(year, deck_id):
    conn = get_db()
    cursor = conn.cursor()
    start_date = date(year, 1, 1).isoformat()
    end_date = date(year + 1, 1, 1).isoformat()
    cursor.execute(
        'SELECT SUM(lessons) as total_lessons, SUM(reviews) as total_reviews FROM activity WHERE date >= ? AND date < ? AND deck_id = ?',
        (start_date, end_date, deck_id))
    row = cursor.fetchone()
    conn.close()
    return {'lessons': row['total_lessons'] or 0, 'reviews': row['total_reviews'] or 0}


def calculate_streaks(end_date, deck_id):
    current_streak = 0
    best_streak = 0
    temp_streak = 0

    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT date, lessons, reviews FROM activity WHERE date <= ? AND deck_id = ? ORDER BY date ASC',
                   (end_date.isoformat(), deck_id))
    rows = cursor.fetchall()
    conn.close()

    act_map = {row['date']: {'lessons': row['lessons'], 'reviews': row['reviews']} for row in rows}

    sorted_dates = sorted([date.fromisoformat(k) for k in act_map.keys()])
    if not sorted_dates:
        return 0, 0

    curr_walk = end_date
    act_today = act_map.get(end_date.isoformat(), {'lessons': 0, 'reviews': 0})
    if act_today['lessons'] == 0 and act_today['reviews'] == 0:
        curr_walk -= timedelta(days=1)

    while curr_walk.isoformat() in act_map:
        act = act_map[curr_walk.isoformat()]
        if act['lessons'] > 0 or act['reviews'] > 0:
            current_streak += 1
            curr_walk -= timedelta(days=1)
        else:
            break

    for d in sorted_dates:
        act = act_map[d.isoformat()]
        if act['lessons'] > 0 or act['reviews'] > 0:
            temp_streak += 1
            if temp_streak > best_streak:
                best_streak = temp_streak
        else:
            temp_streak = 0

    return current_streak, best_streak


def get_current_week_status(ref_date, deck_id):
    week_statuses = []
    for i in range(6, -1, -1):
        d = ref_date - timedelta(days=i)
        act = get_activity_from_db(d, deck_id)
        did_study = (act['lessons'] > 0 or act['reviews'] > 0)
        week_statuses.append((d, did_study))
    return week_statuses


# --- DECK AND CARD LOGIC ---

def get_deck_by_id(deck_id: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT * FROM decks WHERE id = ?', (deck_id,))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None


def get_deck_by_name(deck_name: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT * FROM decks WHERE name = ?', (deck_name,))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None


def update_deck_settings(deck_id: int, daily_lessons: int, daily_reviews: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        UPDATE decks SET daily_lessons = ?, daily_reviews = ? WHERE id = ?
    ''', (daily_lessons, daily_reviews, deck_id))
    conn.commit()
    conn.close()


def get_deck_cards(deck_id: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT * FROM cards WHERE deck_id = ? ORDER BY id ASC', (deck_id,))
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]


def save_card(card_id: int, deck_id: int, word: str, meaning: str, status: str, srs_stage: int, learning_step: int = 0,
              extra_info: str = '{}'):
    if status in ['Unseen', 'Learning']:
        srs_stage = 0
    if status == 'Graduated':
        srs_stage = 9
    if srs_stage == 9:
        status = 'Graduated'

    conn = get_db()
    cursor = conn.cursor()
    if card_id:
        cursor.execute('''
            UPDATE cards 
            SET word = ?, meaning = ?, status = ?, srs_stage = ?, learning_step = ?, extra_info = ?
            WHERE id = ?
        ''', (word, meaning, status, srs_stage, learning_step, extra_info, card_id))
    else:
        cursor.execute('''
            INSERT INTO cards (deck_id, word, meaning, status, srs_stage, learning_step, extra_info)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ''', (deck_id, word, meaning, status, srs_stage, learning_step, extra_info))
    conn.commit()
    conn.close()


def delete_card(card_id: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('DELETE FROM cards WHERE id = ?', (card_id,))
    conn.commit()
    conn.close()


def delete_deck(deck_id: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('DELETE FROM decks WHERE id = ?', (deck_id,))
    conn.commit()
    conn.close()

def ensure_study_logs_exists():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS study_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            deck_id INTEGER,
            date TEXT,
            lessons INTEGER DEFAULT 0,
            reviews INTEGER DEFAULT 0,
            time_spent INTEGER DEFAULT 0,
            UNIQUE(deck_id, date)
        )
    ''')
    conn.commit()
    conn.close()

ensure_study_logs_exists()