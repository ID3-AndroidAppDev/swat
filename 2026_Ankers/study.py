import json
import time
from nicegui import ui
from database import get_db, update_card_srs, log_activity
from state import fn_sidebar_render_container

PRIMARY_COLOR = '#5898d4'

ui.add_head_html('''
    <style>
        html, body, #q-app { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; overflow: hidden !important; background-color: #f4f6f8; }
        .q-page { margin: 0 !important; padding: 0 !important; }
    </style>
''', shared=True)


def get_deck_daily_lessons(deck_id: int) -> int:
    try:
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute("SELECT daily_lessons FROM decks WHERE id = ?", (deck_id,))
        row = cursor.fetchone()
        conn.close()
        if row and row['daily_lessons']: return row['daily_lessons']
    except Exception:
        pass
    return 15


def save_note_to_card(card_id: int, note_text: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT extra_info FROM cards WHERE id = ?", (card_id,))
    row = cursor.fetchone()
    extra = json.loads(row['extra_info']) if row and row['extra_info'] else {}
    notes = extra.get('user_notes', [])
    if isinstance(notes, str): notes = [notes] if notes else []
    notes.append(note_text)
    extra['user_notes'] = notes
    cursor.execute("UPDATE cards SET extra_info = ? WHERE id = ?", (json.dumps(extra), card_id))
    conn.commit()
    conn.close()


def save_synonym_to_card(card_id: int, synonym_text: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT extra_info FROM cards WHERE id = ?", (card_id,))
    row = cursor.fetchone()
    extra = json.loads(row['extra_info']) if row and row['extra_info'] else {}
    syns = extra.get('user_synonyms', [])
    if isinstance(syns, str): syns = [syns] if syns else []
    syns.append(synonym_text)
    extra['user_synonyms'] = syns
    cursor.execute("UPDATE cards SET extra_info = ? WHERE id = ?", (json.dumps(extra), card_id))
    conn.commit()
    conn.close()


def parse_card(row):
    extra = json.loads(row['extra_info']) if row['extra_info'] else {}
    word, meaning = row['word'], row['meaning']

    tabs = extra.get('custom_tabs')
    if not tabs:
        tabs = [
            {'name': 'Meaning', 'layout': '2-column',
             'left': [{'title': 'OTHER MEANINGS', 'text': extra.get('other_meanings', extra.get('info', 'None'))},
                      {'title': 'WORD TYPE', 'text': extra.get('word_type', 'noun')}],
             'right': [{'title': 'MEANING EXPLANATION',
                        'text': extra.get('meaning_explanation', f"Learn the meaning of '{word}': {meaning}.")}]},
            {'name': 'Reading', 'layout': '2-column',
             'left': [{'title': 'READING', 'text': extra.get('reading', word), 'is_main_reading': True}],
             'right': [{'title': 'READING EXPLANATION', 'text': extra.get('reading_explanation',
                                                                          f"The reading for '{word}' is '{extra.get('reading', word)}'.")}]}
        ]
        comp_text = "\n".join(
            [f"{i.get('character', '')} - {i.get('meaning', '')}" for i in extra.get('composition', [])])
        if comp_text.strip(): tabs.append({'name': 'Composition', 'layout': '1-column',
                                           'single': [{'title': 'KANJI COMPOSITION', 'text': comp_text.strip()}]})

        ctx_text = "\n\n".join([f"{s.get('ja', '')}\n{s.get('en', '')}" for s in extra.get('context_sentences', [])])
        if ctx_text.strip(): tabs.append({'name': 'Context', 'layout': '1-column',
                                          'single': [{'title': 'CONTEXT SENTENCES', 'text': ctx_text.strip()}]})

    return {
        'id': row['id'], 'deck_id': row['deck_id'], 'word': word, 'meaning': meaning,
        'status': row['status'], 'custom_tabs': tabs,
        'user_notes': extra.get('user_notes', []) if isinstance(extra.get('user_notes', []), list) else [],
        'user_synonyms': extra.get('user_synonyms', []) if isinstance(extra.get('user_synonyms', []), list) else [],
        'seen': extra.get('seen', False), 'needs_relesson': extra.get('needs_relesson', False)
    }


def get_cards_for_study(deck_id: int = None, limit: int = None):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM cards WHERE deck_id = ? AND status IN ('New', 'Unseen', 'Learning')",
                   (deck_id,)) if deck_id else cursor.execute(
        "SELECT * FROM cards WHERE status IN ('New', 'Unseen', 'Learning')")
    rows = cursor.fetchall()
    conn.close()
    cards = [parse_card(r) for r in rows]
    return cards[:limit] if limit and limit > 0 else cards


@ui.page('/study/{deck_id}')
def study_page(deck_id: int, limit: int = None):
    loaded_cards = get_cards_for_study(deck_id,
                                       limit=limit if (limit and limit > 0) else get_deck_daily_lessons(deck_id))

    state = {
        'idx': 0, 'active_tab_idx': 0, 'start_time': time.time(),
        'last_checkpoint': time.time(), 'queue': loaded_cards,
        'completed': False, 'reveal_override': False, 'dialog_open': False,
        'is_flipped': False
    }

    def flush_study_time():
        now = time.time()
        elapsed = int(now - state['last_checkpoint'])
        if elapsed > 0:
            log_activity(deck_id, time_spent=elapsed)
            state['last_checkpoint'] = now

    def process_rating(rating: int):
        if state['completed'] or not state['queue']:
            flush_study_time()
            if fn_sidebar_render_container: fn_sidebar_render_container()
            ui.navigate.to('/')
            return
        flush_study_time()
        current_card = state['queue'][state['idx']]
        needs_relesson = True if rating in (1, 2) else False

        result = update_card_srs(current_card['id'], rating)
        current_card['seen'] = True
        current_card['needs_relesson'] = needs_relesson

        if result['status'] in ('Reviewing', 'Graduated'):
            log_activity(deck_id, lessons=1)
            state['queue'].pop(state['idx'])
        else:
            state['queue'].append(state['queue'].pop(state['idx']))

        state['reveal_override'] = False
        state['is_flipped'] = False
        state['active_tab_idx'] = 0

        if not state['queue']:
            state['completed'] = True
            flush_study_time()
            if fn_sidebar_render_container: fn_sidebar_render_container()
            ui.navigate.to('/')
            return
        if state['idx'] >= len(state['queue']): state['idx'] = 0
        refresh_all()

    def flip_card():
        state['is_flipped'] = True
        state['active_tab_idx'] = 0
        refresh_all()

    def next_tab():
        if not state['queue']: return
        tabs = state['queue'][state['idx']].get('custom_tabs', [])
        if state['active_tab_idx'] < len(tabs) - 1:
            state['active_tab_idx'] += 1
            render_tabs.refresh()
            render_card_content.refresh()

    def prev_tab():
        if state['active_tab_idx'] > 0:
            state['active_tab_idx'] -= 1
            render_tabs.refresh()
            render_card_content.refresh()

    def handle_key(e):
        if state.get('dialog_open', False) or not e.action.keydown or e.action.repeat: return
        k = str(e.key)
        current_card = state['queue'][state['idx']]
        show_full_lesson = not current_card.get('seen', False) or current_card.get('needs_relesson',
                                                                                   False) or state.get(
            'reveal_override', False)

        if not show_full_lesson and not state['is_flipped'] and k in ('Enter', ' ', 'Space'):
            flip_card()
        elif show_full_lesson or state['is_flipped']:
            if k in ('1', '2', '3', '4'):
                process_rating(int(k))
            elif k == 'Numpad1':
                process_rating(1)
            elif k == 'Numpad2':
                process_rating(2)
            elif k == 'Numpad3':
                process_rating(3)
            elif k == 'Numpad4':
                process_rating(4)
            elif k == 'ArrowRight':
                next_tab()
            elif k == 'ArrowLeft':
                prev_tab()

    ui.keyboard(on_key=handle_key)
    note_dialog = ui.dialog()
    synonym_dialog = ui.dialog()
    ui.timer(10.0, flush_study_time)

    with ui.column().classes(
            'fixed inset-0 w-screen h-screen p-0 m-0 gap-0 select-none flex flex-col bg-slate-50 overflow-hidden'):
        @ui.refreshable
        def render_header():
            if state['completed'] or not state['queue']: return
            current_card = state['queue'][state['idx']]
            show_full_lesson = not current_card.get('seen', False) or current_card.get('needs_relesson',
                                                                                       False) or state.get(
                'reveal_override', False)
            header_height_cls = 'h-1/2' if show_full_lesson else 'h-[90%]'

            with ui.element('div').classes(
                    f'w-full {header_height_cls} shrink-0 flex flex-col items-center justify-center relative transition-all duration-300').style(
                    f'background-color: {PRIMARY_COLOR};'):
                with ui.row().classes('absolute top-5 left-6 items-center'):
                    ui.icon('home', size='28px').classes('text-white cursor-pointer opacity-90 hover:opacity-100').on(
                        'click', lambda: (flush_study_time(), ui.navigate.to('/')))
                with ui.column().classes('absolute top-5 right-6 items-end gap-1 text-white opacity-90'):
                    ui.label(f"Cards Left: {len(state['queue'])}").style('font-size: 18px; font-weight: 700;')
                    timer_label = ui.label('00:00:00').style('font-size: 16px; font-family: monospace;')

                    def update_timer():
                        elapsed = int(time.time() - state['start_time'])
                        timer_label.set_text(f'{elapsed // 3600:02d}:{(elapsed % 3600) // 60:02d}:{elapsed % 60:02d}')

                    ui.timer(1.0, update_timer)

                with ui.column().classes('items-center justify-center gap-3 my-auto cursor-pointer').on('click',
                                                                                                        lambda: None if (
                                                                                                                show_full_lesson or
                                                                                                                state[
                                                                                                                    'is_flipped']) else flip_card()):
                    ui.label(current_card['word']).style(
                        'color: #ffffff; font-size: 110px; font-weight: 500; line-height: 1;')
                    if show_full_lesson or state['is_flipped']:
                        ui.label(current_card['meaning']).style('color: #ffffff; font-size: 32px; font-weight: 400;')
                    else:
                        ui.label('Click or press Space to reveal').style(
                            'color: rgba(255,255,255,0.7); font-size: 16px; font-weight: 400;')

        render_header()

        with ui.column().classes('w-full flex-1 flex flex-col justify-between p-0 m-0 overflow-hidden'):
            @ui.refreshable
            def render_tabs():
                if state['completed'] or not state['queue']: return
                current_card = state['queue'][state['idx']]
                show_full_lesson = not current_card.get('seen', False) or current_card.get('needs_relesson',
                                                                                           False) or state.get(
                    'reveal_override', False)
                if not show_full_lesson: return

                tabs = current_card.get('custom_tabs', [])
                with ui.element('div').classes(
                        'w-full h-12 shrink-0 flex justify-center items-center border-b border-slate-300 bg-slate-200 z-10'):
                    for i, t in enumerate(tabs):
                        is_active = (state['active_tab_idx'] == i)
                        text_cls = 'text-slate-900 font-bold text-base' if is_active else 'text-slate-500 hover:text-slate-700 font-medium text-base'
                        with ui.element('div').classes(
                                'h-full flex items-center justify-center px-8 cursor-pointer relative').on('click',
                                                                                                           lambda tab_idx=i: (
                                                                                                                   state.update(
                                                                                                                           {
                                                                                                                               'active_tab_idx': tab_idx}),
                                                                                                                   render_tabs.refresh(),
                                                                                                                   render_card_content.refresh())):
                            ui.label(t['name']).classes(f'tracking-wide {text_cls}')
                            if is_active: ui.element('div').classes(
                                'absolute bottom-0 left-1/2 -translate-x-1/2').style(
                                'width: 0; height: 0; border-left: 7px solid transparent; border-right: 7px solid transparent; border-bottom: 7px solid #f4f6f8;')

            render_tabs()

            @ui.refreshable
            def render_card_content():
                if state['completed'] or not state['queue']: return
                current_card = state['queue'][state['idx']]
                if not current_card.get('seen', False):
                    current_card['seen'] = True
                    current_card['needs_relesson'] = True

                show_full_lesson = current_card.get('needs_relesson', False) or state.get('reveal_override', False)
                if not show_full_lesson: return

                tabs = current_card.get('custom_tabs', [])
                if not tabs: return

                curr_i = state.get('active_tab_idx', 0)
                if curr_i >= len(tabs): curr_i = 0
                active_tab = tabs[curr_i]

                is_first_tab = (curr_i == 0)
                is_last_tab = (curr_i == len(tabs) - 1)

                with ui.row().classes('flex-1 w-full items-stretch p-0 m-0 overflow-hidden relative'):
                    prev_btn_cls = 'opacity-30 cursor-not-allowed' if is_first_tab else 'hover:bg-slate-200 hover:text-slate-800 cursor-pointer'
                    ui.button('<', on_click=prev_tab).classes(
                        f'h-full rounded-none bg-transparent text-slate-500 px-6 shadow-none border-none text-4xl font-light z-10 {prev_btn_cls}').props(
                        'flat')

                    with ui.row().classes('flex-1 items-start py-5 px-8 overflow-y-auto justify-center h-full'):
                        with ui.column().classes('w-full max-w-4xl gap-5 text-left'):

                            def render_field(f):
                                with ui.column().classes('gap-1 w-full pt-1'):
                                    ui.label(f.get('title', '')).classes(
                                        'text-sm font-bold text-slate-400 tracking-wider uppercase')
                                    if f.get('is_main_reading'):
                                        ui.label(f.get('text', '')).style(f'color: {PRIMARY_COLOR};').classes(
                                            'text-4xl font-extrabold')
                                    else:
                                        ui.label(f.get('text', '')).classes(
                                            'text-base text-slate-700 leading-relaxed break-words').style(
                                            'white-space: pre-wrap;')

                            def render_notes_and_synonyms():
                                with ui.column().classes('gap-1 w-full pt-2 border-t border-slate-200 mt-2'):
                                    ui.label('User Notes').classes(
                                        'text-sm font-bold text-slate-400 tracking-wider uppercase')
                                    if current_card.get('user_notes'):
                                        for note in current_card['user_notes']: ui.label(note).classes(
                                            'text-base text-slate-800 border-b border-slate-200 pb-1 w-full').style(
                                            'white-space: pre-wrap;')
                                    else:
                                        ui.label('No notes added yet.').classes('text-sm text-slate-400 italic')
                                    ui.button('+ Add Note', on_click=lambda: (state.update({'dialog_open': True}),
                                                                              note_dialog.open())).style(
                                        f'color: {PRIMARY_COLOR};').classes(
                                        'p-0 text-sm font-bold shadow-none cursor-pointer normal-case').props(
                                        'flat dense')

                                with ui.column().classes('gap-1 w-full pt-1'):
                                    ui.label('User Synonyms').classes(
                                        'text-sm font-bold text-slate-400 tracking-wider uppercase')
                                    if current_card.get('user_synonyms'):
                                        with ui.row().classes('gap-2 flex-wrap'):
                                            for syn in current_card['user_synonyms']: ui.label(syn).classes(
                                                'bg-slate-200 text-slate-800 px-3 py-1 rounded-lg text-sm font-medium')
                                    else:
                                        ui.label('No user synonyms added yet.').classes('text-sm text-slate-400 italic')
                                    ui.button('+ Add Synonym', on_click=lambda: (state.update({'dialog_open': True}),
                                                                                 synonym_dialog.open())).style(
                                        f'color: {PRIMARY_COLOR};').classes(
                                        'p-0 text-sm font-bold shadow-none cursor-pointer normal-case').props(
                                        'flat dense')

                            if active_tab.get('layout') == '2-column':
                                with ui.row().classes('w-full gap-8 items-start'):
                                    with ui.column().classes('w-64 gap-5 shrink-0'):
                                        for f in active_tab.get('left', []): render_field(f)
                                    with ui.column().classes('flex-1 gap-5 min-w-0'):
                                        for f in active_tab.get('right', []): render_field(f)
                                        if is_first_tab: render_notes_and_synonyms()
                            else:
                                with ui.column().classes('w-full gap-5'):
                                    for f in active_tab.get('single', []): render_field(f)
                                    if is_first_tab: render_notes_and_synonyms()

                    next_btn_cls = 'opacity-30 cursor-not-allowed' if is_last_tab else 'hover:bg-slate-200 hover:text-slate-800 cursor-pointer'
                    ui.button('>', on_click=next_tab).classes(
                        f'h-full rounded-none bg-transparent text-slate-500 px-6 shadow-none border-none text-4xl font-light z-10 {next_btn_cls}').props(
                        'flat')

            render_card_content()

            @ui.refreshable
            def render_bottom_bar():
                if state['completed'] or not state['queue']: return
                current_card = state['queue'][state['idx']]
                show_full_lesson = not current_card.get('seen', False) or current_card.get('needs_relesson',
                                                                                           False) or state.get(
                    'reveal_override', False)

                if not show_full_lesson and not state['is_flipped']:
                    with ui.row().classes(
                            'w-full shrink-0 items-center justify-center py-4 border-t border-slate-200 bg-white z-10'):
                        ui.button('Show Answer', on_click=flip_card).classes(
                            'bg-slate-900 hover:bg-slate-800 text-white font-bold px-12 py-3 rounded-xl text-lg normal-case shadow-sm cursor-pointer')
                else:
                    with ui.row().classes(
                            'w-full shrink-0 items-center justify-center gap-5 py-4 border-t border-slate-200 bg-white z-10'):
                        ui.button('1 : Again', on_click=lambda: process_rating(1)).classes(
                            'bg-red-500 hover:bg-red-600 text-white font-bold px-8 py-3 rounded-xl text-base normal-case shadow-sm cursor-pointer')
                        ui.button('2 : Hard', on_click=lambda: process_rating(2)).classes(
                            'bg-amber-500 hover:bg-amber-600 text-white font-bold px-8 py-3 rounded-xl text-base normal-case shadow-sm cursor-pointer')
                        ui.button('3 : Good', on_click=lambda: process_rating(3)).style(
                            f'background-color: {PRIMARY_COLOR};').classes(
                            'text-white font-bold px-8 py-3 rounded-xl text-base normal-case shadow-sm cursor-pointer')
                        ui.button('4 : Easy', on_click=lambda: process_rating(4)).classes(
                            'bg-emerald-500 hover:bg-emerald-600 text-white font-bold px-8 py-3 rounded-xl text-base normal-case shadow-sm cursor-pointer')

            render_bottom_bar()

    def close_dialog(dialog):
        state['dialog_open'] = False
        dialog.close()

    with note_dialog, ui.card().classes('rounded-2xl p-6 bg-white shadow-2xl w-96 gap-4'):
        ui.label('Add Note').classes('text-xl font-bold text-slate-800')
        note_field = ui.input(placeholder='Enter note here...').classes('w-full text-base')

        def submit_note():
            val = note_field.value.strip()
            if val and state['queue']:
                curr = state['queue'][state['idx']]
                curr['user_notes'].append(val)
                save_note_to_card(curr['id'], val)
                note_field.set_value('')
                close_dialog(note_dialog)
                render_card_content.refresh()

        with ui.row().classes('w-full justify-end gap-2 pt-2'):
            ui.button('Cancel', color='transparent', on_click=lambda: close_dialog(note_dialog)).classes(
                'text-slate-500 text-base')
            ui.button('Save', on_click=submit_note).style(f'background-color: {PRIMARY_COLOR};').classes(
                'text-white font-bold text-base')

    with synonym_dialog, ui.card().classes('rounded-2xl p-6 bg-white shadow-2xl w-96 gap-4'):
        ui.label('Add User Synonym').classes('text-xl font-bold text-slate-800')
        synonym_field = ui.input(placeholder='Enter synonym here...').classes('w-full text-base')

        def submit_synonym():
            val = synonym_field.value.strip()
            if val and state['queue']:
                curr = state['queue'][state['idx']]
                curr['user_synonyms'].append(val)
                save_synonym_to_card(curr['id'], val)
                synonym_field.set_value('')
                close_dialog(synonym_dialog)
                render_card_content.refresh()

        with ui.row().classes('w-full justify-end gap-2 pt-2'):
            ui.button('Cancel', color='transparent', on_click=lambda: close_dialog(synonym_dialog)).classes(
                'text-slate-500 text-base')
            ui.button('Save', on_click=submit_synonym).style(f'background-color: {PRIMARY_COLOR};').classes(
                'text-white font-bold text-base')

    def refresh_all():
        render_header.refresh()
        render_tabs.refresh()
        render_card_content.refresh()
        render_bottom_bar.refresh()