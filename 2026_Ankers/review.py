import json
import time
from datetime import datetime
from nicegui import ui
from database import get_db, update_card_srs, log_activity
from state import fn_sidebar_render_container

PRIMARY_COLOR = '#5898d4'

ui.add_head_html('''
    <style>
        html, body, #q-app { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; overflow: hidden !important; background-color: #f4f6f8; }
        .q-page { margin: 0 !important; padding: 0 !important; }
        .shake { animation: shake 0.5s cubic-bezier(.36,.07,.19,.97) both; }
        @keyframes shake { 10%, 90% { transform: translate3d(-1px, 0, 0); } 20%, 80% { transform: translate3d(2px, 0, 0); } 30%, 50%, 70% { transform: translate3d(-4px, 0, 0); } 40%, 60% { transform: translate3d(4px, 0, 0); } }
    </style>
''', shared=True)


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
        'other_meanings': extra.get('other_meanings', ''),
        'user_notes': extra.get('user_notes', []) if isinstance(extra.get('user_notes', []), list) else [],
        'user_synonyms': extra.get('user_synonyms', []) if isinstance(extra.get('user_synonyms', []), list) else []
    }


def get_cards_for_review(deck_id: int, limit: int = None, is_extra: bool = False):
    conn = get_db()
    cursor = conn.cursor()
    if is_extra:
        cursor.execute(
            "SELECT * FROM cards WHERE deck_id = ? AND status IN ('Reviewing', 'Graduated') ORDER BY RANDOM()",
            (deck_id,))
    else:
        now_str = datetime.now().isoformat()
        cursor.execute(
            "SELECT * FROM cards WHERE deck_id = ? AND status IN ('Reviewing', 'Graduated') AND (next_review_at <= ? OR next_review_at IS NULL)",
            (deck_id, now_str))
    rows = cursor.fetchall()
    conn.close()
    cards = [parse_card(r) for r in rows]
    return cards[:limit] if limit and limit > 0 else cards


def get_review_mode(deck_id: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT value FROM settings WHERE key = ?', (f'deck_{deck_id}_review_mode',))
    row = cursor.fetchone()
    conn.close()
    return row['value'] if row else 'Anki'


def check_wani_answer(user_input, card):
    answers = [card['meaning'].lower().strip()]
    if card.get('other_meanings') and card['other_meanings'] != 'None':
        answers.extend([m.strip().lower() for m in card['other_meanings'].split(',')])
    if card.get('user_synonyms'):
        answers.extend([s.strip().lower() for s in card['user_synonyms']])
    return user_input.lower().strip() in answers


@ui.page('/review/{deck_id}')
def review_page(deck_id: int, limit: int = None, extra: str = 'false'):
    is_extra = (extra.lower() == 'true')
    loaded_cards = get_cards_for_review(deck_id, limit=limit, is_extra=is_extra)
    mode = get_review_mode(deck_id)

    state = {
        'idx': 0, 'active_tab_idx': 0, 'start_time': time.time(), 'elapsed_str': '00:00:00',
        'queue': loaded_cards, 'completed': False, 'is_flipped': False,
        'review_mode': mode, 'wani_backspaces': 0, 'wani_wrong_enters': 0, 'pending_rating': None
    }

    def update_time():
        if state['completed'] or not state['queue']: return
        elapsed = int(time.time() - state['start_time'])
        state['elapsed_str'] = f"{elapsed // 3600:02d}:{(elapsed % 3600) // 60:02d}:{elapsed % 60:02d}"

    ui.timer(1.0, update_time)

    if not state['queue']:
        with ui.column().classes(
                'fixed inset-0 w-screen h-screen items-center justify-center bg-slate-50 gap-4 p-6 text-center'):
            ui.icon('check_circle', size='5rem').classes('text-emerald-500 mb-2')
            if is_extra:
                ui.label('No cards available!').classes('text-3xl font-black text-slate-800')
                ui.label("You need to complete some lessons before you can practice.").classes(
                    'text-base text-slate-500 mb-4 max-w-md')
            else:
                ui.label('No reviews pending!').classes('text-3xl font-black text-slate-800')
                ui.label("You're all caught up for now. Check back later!").classes(
                    'text-base text-slate-500 mb-4 max-w-md')
            ui.button('Back to Dashboard', on_click=lambda: ui.navigate.to('/')).classes(
                'bg-slate-900 hover:bg-slate-800 text-white font-bold px-8 py-3 rounded-xl shadow-sm cursor-pointer')
        return

    def process_rating(rating: int):
        if state['completed'] or not state['queue']:
            state['completed'] = True
            if fn_sidebar_render_container: fn_sidebar_render_container()
            ui.navigate.to('/')
            return

        current_card = state['queue'][state['idx']]
        needs_rereview = True if rating in (1, 2) else False

        if not is_extra:
            update_card_srs(current_card['id'], rating)
            if not needs_rereview: log_activity(deck_id, reviews=1)

        if not needs_rereview:
            state['queue'].pop(state['idx'])
        else:
            state['queue'].append(state['queue'].pop(state['idx']))

        state['is_flipped'] = False
        state['active_tab_idx'] = 0
        state['wani_backspaces'] = 0
        state['wani_wrong_enters'] = 0
        state['pending_rating'] = None

        if not state['queue']:
            state['completed'] = True
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
        if not e.action.keydown or e.action.repeat: return
        k = str(e.key)

        if k == 'Backspace': state['wani_backspaces'] += 1; return

        if state['review_mode'] == 'Wani':
            if state['is_flipped'] and k in ('Enter', ' '):
                process_rating(state['pending_rating'] if state['pending_rating'] else 4)
            return

        if not state['is_flipped'] and k in ('Enter', ' ', 'Space'):
            flip_card()
        elif state['is_flipped']:
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

    with ui.column().classes(
            'fixed inset-0 w-screen h-screen p-0 m-0 gap-0 select-none flex flex-col bg-slate-50 overflow-hidden'):
        @ui.refreshable
        def render_header():
            if state['completed'] or not state['queue']: return
            current_card = state['queue'][state['idx']]

            header_height_cls = 'h-[90%]' if (not state['is_flipped'] or state['review_mode'] == 'Anki') else 'h-1/2'

            with ui.element('div').classes(
                    f'w-full {header_height_cls} shrink-0 flex flex-col items-center justify-center relative transition-all duration-300').style(
                    f'background-color: {PRIMARY_COLOR};'):
                with ui.row().classes('absolute top-5 left-6 items-center'):
                    ui.icon('home', size='28px').classes('text-white cursor-pointer opacity-90 hover:opacity-100').on(
                        'click', lambda: ui.navigate.to('/'))
                with ui.column().classes('absolute top-5 right-6 items-end gap-1 text-white opacity-90'):
                    ui.label(f"Reviews Left: {len(state['queue'])}").style('font-size: 18px; font-weight: 700;')
                    ui.label().bind_text_from(state, 'elapsed_str').style('font-size: 16px; font-family: monospace;')

                with ui.column().classes('items-center justify-center gap-3 my-auto w-full max-w-2xl px-6'):
                    if state['review_mode'] == 'Anki':
                        with ui.column().classes('items-center cursor-pointer w-full').on('click',
                                                                                          lambda: None if state[
                                                                                              'is_flipped'] else flip_card()):
                            ui.label(current_card['word']).style(
                                'color: #ffffff; font-size: 110px; font-weight: 500; line-height: 1;')
                            if state['is_flipped']:
                                ui.label(current_card['meaning']).style(
                                    'color: #ffffff; font-size: 32px; font-weight: 400;')
                            else:
                                ui.label('Click or press Space to reveal').style(
                                    'color: rgba(255,255,255,0.7); font-size: 16px; font-weight: 400;')

                    elif state['review_mode'] == 'Wani':
                        ui.label(current_card['word']).style(
                            'color: #ffffff; font-size: 110px; font-weight: 500; line-height: 1;')

                        if state['is_flipped']:
                            ui.label(current_card['meaning']).style(
                                'color: #ffffff; font-size: 32px; font-weight: 400;')
                        else:
                            wani_input = ui.input(placeholder='Type meaning (English)...').classes(
                                'w-full max-w-lg text-2xl bg-white/20 rounded-xl px-4 py-2 text-white placeholder:text-white/60 text-center font-bold').props(
                                'autofocus outline-none border-none border-0')

                            def submit_wani():
                                val = wani_input.value
                                if not val or not val.strip(): return
                                if check_wani_answer(val, current_card):
                                    if state['wani_wrong_enters'] >= 1 or state['wani_backspaces'] >= 2:
                                        r = 2
                                    elif state['wani_backspaces'] == 1:
                                        r = 3
                                    else:
                                        r = 4
                                    process_rating(r)
                                else:
                                    state['wani_wrong_enters'] += 1
                                    wani_input.set_value('')
                                    if state['wani_wrong_enters'] >= 2:
                                        state['pending_rating'] = 1
                                        flip_card()
                                    else:
                                        wani_input.classes(add='shake')
                                        ui.timer(0.5, lambda: wani_input.classes(remove='shake'), once=True)
                                        ui.notify('Incorrect. Try again!', color='warning')

                            wani_input.on('keydown.enter', submit_wani)

        render_header()

        with ui.column().classes('w-full flex-1 flex flex-col justify-between p-0 m-0 overflow-hidden'):
            @ui.refreshable
            def render_tabs():
                if state['completed'] or not state['queue'] or not state['is_flipped'] or state[
                    'review_mode'] == 'Anki': return
                tabs = state['queue'][state['idx']].get('custom_tabs', [])
                if not tabs: return
                with ui.element('div').classes(
                        'w-full h-12 shrink-0 flex justify-center items-center border-b border-slate-300 bg-slate-200 z-10'):
                    for i, t in enumerate(tabs):
                        is_active = (state['active_tab_idx'] == i)
                        text_cls = 'text-slate-900 font-bold' if is_active else 'text-slate-500 hover:text-slate-700 font-medium'
                        with ui.element('div').classes(
                                'h-full flex items-center justify-center px-8 cursor-pointer relative').on('click',
                                                                                                           lambda tab_idx=i: (
                                                                                                                   state.update(
                                                                                                                           {
                                                                                                                               'active_tab_idx': tab_idx}),
                                                                                                                   render_tabs.refresh(),
                                                                                                                   render_card_content.refresh())):
                            ui.label(t['name']).classes(f'tracking-wide text-base {text_cls}')
                            if is_active: ui.element('div').classes(
                                'absolute bottom-0 left-1/2 -translate-x-1/2').style(
                                'width: 0; height: 0; border-left: 7px solid transparent; border-right: 7px solid transparent; border-bottom: 7px solid #f4f6f8;')

            render_tabs()

            @ui.refreshable
            def render_card_content():
                if state['completed'] or not state['queue'] or not state['is_flipped'] or state[
                    'review_mode'] == 'Anki': return
                current_card = state['queue'][state['idx']]
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

                            def render_read_only_notes():
                                with ui.column().classes('gap-1 w-full pt-2 border-t border-slate-200 mt-2'):
                                    ui.label('User Notes').classes(
                                        'text-sm font-bold text-slate-400 tracking-wider uppercase')
                                    if current_card.get('user_notes'):
                                        for note in current_card['user_notes']: ui.label(note).classes(
                                            'text-base text-slate-800 border-b border-slate-200 pb-1 w-full').style(
                                            'white-space: pre-wrap;')
                                    else:
                                        ui.label('No notes added yet.').classes('text-sm text-slate-400 italic')
                                with ui.column().classes('gap-1 w-full pt-1'):
                                    ui.label('User Synonyms').classes(
                                        'text-sm font-bold text-slate-400 tracking-wider uppercase')
                                    if current_card.get('user_synonyms'):
                                        with ui.row().classes('gap-2 flex-wrap'):
                                            for syn in current_card['user_synonyms']: ui.label(syn).classes(
                                                'bg-slate-200 text-slate-800 px-3 py-1 rounded-lg text-sm font-medium')
                                    else:
                                        ui.label('No user synonyms added yet.').classes('text-sm text-slate-400 italic')

                            if active_tab.get('layout') == '2-column':
                                with ui.row().classes('w-full gap-8 items-start'):
                                    with ui.column().classes('w-64 gap-5 shrink-0'):
                                        for f in active_tab.get('left', []): render_field(f)
                                    with ui.column().classes('flex-1 gap-5 min-w-0'):
                                        for f in active_tab.get('right', []): render_field(f)
                                        if is_first_tab: render_read_only_notes()
                            else:
                                with ui.column().classes('w-full gap-5'):
                                    for f in active_tab.get('single', []): render_field(f)
                                    if is_first_tab: render_read_only_notes()

                    next_btn_cls = 'opacity-30 cursor-not-allowed' if is_last_tab else 'hover:bg-slate-200 hover:text-slate-800 cursor-pointer'
                    ui.button('>', on_click=next_tab).classes(
                        f'h-full rounded-none bg-transparent text-slate-500 px-6 shadow-none border-none text-4xl font-light z-10 {next_btn_cls}').props(
                        'flat')

            render_card_content()

            @ui.refreshable
            def render_bottom_bar():
                if state['completed'] or not state['queue']: return
                if state['review_mode'] == 'Wani':
                    if state['is_flipped']:
                        with ui.row().classes(
                                'w-full shrink-0 items-center justify-center py-4 border-t border-slate-200 bg-white z-10'):
                            ui.button('Next (Enter)', on_click=lambda: process_rating(state['pending_rating'])).classes(
                                'bg-slate-900 hover:bg-slate-800 text-white font-bold px-12 py-3 rounded-xl text-lg normal-case shadow-sm cursor-pointer')
                    return
                if not state['is_flipped']:
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

    def refresh_all():
        render_header.refresh()
        render_tabs.refresh()
        render_card_content.refresh()
        render_bottom_bar.refresh()