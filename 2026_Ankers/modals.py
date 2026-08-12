import json
from nicegui import ui, app
from database import (
    get_db, get_deck_by_id, get_deck_by_name, update_deck_settings,
    get_deck_cards, save_card, delete_card, delete_deck
)
from state import app_state
import state


# --- MAIN SETTINGS MODAL ---
main_form_state = {'is_dirty': False}
main_settings_inputs = {}


def open_main_settings():
    settings_dialog = ui.dialog()

    # fetch current settings from database
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT value FROM settings WHERE key = ?', ('custom_study_mode',))
    c_row = cursor.fetchone()
    conn.close()

    current_custom_study = (c_row and c_row['value'] == 'true')

    with settings_dialog, ui.card().classes('w-full max-w-md p-6 bg-white rounded-2xl shadow-2xl gap-6'):
        # Header row
        with ui.row().classes('w-full justify-between items-center border-b pb-3'):
            ui.label('Settings').classes('text-xl font-bold text-slate-800')
            ui.button(icon='close', on_click=settings_dialog.close).props('flat round dense').classes('text-slate-400')

        # Settings options column
        with ui.column().classes('w-full gap-4'):
            # 1. Dark mode (i dont wanna implement this...)
            with ui.row().classes('w-full justify-between items-center py-1'):
                with ui.column().classes('gap-0'):
                    ui.label('Dark Mode').classes('text-base font-semibold text-slate-800')
                    ui.label('(not available in the current version)').classes('text-xs text-slate-400')
                ui.switch(value=False).props('disable')

            ui.element('div').classes('w-full h-[1px] bg-slate-100')

            # 2. Custom study mode toggle
            with ui.row().classes('w-full justify-between items-center py-1'):
                with ui.column().classes('gap-0 flex-1 pr-4'):
                    ui.label('Custom Study Mode').classes('text-base font-semibold text-slate-800')
                    ui.label('Allows manually editing Status and SRS Level of cards in Deck Settings.').classes(
                        'text-xs text-slate-500')

                custom_study_switch = ui.switch(value=current_custom_study)

        # Save Button
        def save_main_settings():
            conn_s = get_db()
            cursor_s = conn_s.cursor()
            cursor_s.execute(
                'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
                ('custom_study_mode', 'true' if custom_study_switch.value else 'false')
            )
            conn_s.commit()
            conn_s.close()

            ui.notify('Settings updated successfully!', type='positive')
            settings_dialog.close()

            # refresh sidebar or main view if need
            if state.fn_sidebar_render_container:
                state.fn_sidebar_render_container()

        with ui.row().classes('w-full justify-end pt-2'):
            ui.button('SAVE', on_click=save_main_settings).classes(
                'bg-slate-900 text-white font-semibold px-6 py-2 rounded-xl shadow-sm cursor-pointer')

    settings_dialog.open()


# --- FOLDER & DECK CREATION / EDIT MODALS ---
form_state = {'is_dirty': False, 'target': None, 'target_type': None}
folder_inputs = {}
deck_inputs = {}


def open_add_menu():
    add_choice_dialog = ui.dialog()
    with add_choice_dialog, ui.card().classes('rounded-xl p-6 bg-white shadow-xl w-80 gap-4'):
        ui.label('Create New').classes('text-lg font-bold text-slate-800')
        with ui.row().classes('w-full justify-between gap-3'):
            ui.button('Folder', on_click=lambda: [add_choice_dialog.close(), open_create_folder_modal()]).classes(
                'flex-1 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold py-2.5 rounded-xl shadow-none cursor-pointer')
            ui.button('Deck', on_click=lambda: [add_choice_dialog.close(), open_create_deck_modal()]).classes(
                'flex-1 bg-slate-900 text-white font-semibold py-2.5 rounded-xl shadow-none cursor-pointer')
    add_choice_dialog.open()


def open_create_folder_modal():
    create_folder_dialog = ui.dialog()
    with create_folder_dialog, ui.card().classes('rounded-xl p-6 bg-white shadow-xl w-[400px] max-w-full gap-4'):
        ui.label('New Folder').classes('text-lg font-bold text-slate-800')

        folder_name_input = ui.input('Folder Name').classes('w-full').props('autofocus')
        folder_name_input.on('keydown.enter', lambda: save_new_folder(folder_name_input.value, create_folder_dialog))

        with ui.row().classes('w-full justify-end gap-2 pt-2'):
            ui.button('Cancel', color='transparent', on_click=create_folder_dialog.close).classes(
                'text-slate-500 font-medium shadow-none cursor-pointer')
            ui.button('Create',
                      on_click=lambda: save_new_folder(folder_name_input.value, create_folder_dialog)).classes(
                'bg-slate-900 text-white font-semibold cursor-pointer')
    create_folder_dialog.open()


def save_new_folder(name, dialog):
    if not name or not name.strip():
        ui.notify('Folder name cannot be empty', color='negative')
        return
    conn = get_db()
    cursor = conn.cursor()
    try:
        cursor.execute('INSERT INTO folders (name) VALUES (?)', (name.strip(),))
        conn.commit()
    except Exception:
        ui.notify('Folder name might already exist', color='negative')
    finally:
        conn.close()
    dialog.close()

    # properly refresh ui bc it doesnt update
    if app_state.get('refresh_lessons'):
        app_state['refresh_lessons']()


def open_create_deck_modal():
    create_deck_dialog = ui.dialog()
    with create_deck_dialog, ui.card().classes('rounded-xl p-6 bg-white shadow-xl w-[400px] max-w-full gap-4'):
        ui.label('New Deck').classes('text-lg font-bold text-slate-800')

        deck_name_input = ui.input('Deck Name').classes('w-full').props('autofocus')
        deck_name_input.on('keydown.enter', lambda: save_new_deck(deck_name_input.value, create_deck_dialog))

        with ui.row().classes('w-full justify-end gap-2 pt-2'):
            ui.button('Cancel', color='transparent', on_click=create_deck_dialog.close).classes(
                'text-slate-500 font-medium shadow-none cursor-pointer')
            ui.button('Create', on_click=lambda: save_new_deck(deck_name_input.value, create_deck_dialog)).classes(
                'bg-slate-900 text-white font-semibold cursor-pointer')
    create_deck_dialog.open()


def save_new_deck(name, dialog):
    if not name or not name.strip():
        ui.notify('Deck name cannot be empty', color='negative')
        return
    conn = get_db()
    cursor = conn.cursor()
    try:
        cursor.execute('INSERT INTO decks (name, folder_id, daily_lessons) VALUES (?, NULL, 10)', (name.strip(),))
        conn.commit()
        app_state['current_deck'] = name.strip()
        if app_state.get('title_label'):
            app_state['title_label'].text = name.strip()
    except Exception:
        ui.notify('Deck name might already exist', color='negative')
    finally:
        conn.close()
    dialog.close()

    # properly refresh ui bc it doesnt update
    if app_state.get('refresh_lessons'):
        app_state['refresh_lessons']()
    if app_state.get('refresh_heatmap'):
        app_state['refresh_heatmap']()


# --- SETTINGS & EDIT MODALS ---

def open_settings(target_name, target_type):
    form_state['target'] = target_name
    form_state['target_type'] = target_type
    form_state['is_dirty'] = False

    conn = get_db()
    cursor = conn.cursor()

    if target_type == 'deck':
        cursor.execute('SELECT id FROM decks WHERE name = ?', (target_name,))
        row = cursor.fetchone()
        conn.close()
        if row:
            open_deck_editor_modal(row['id'])
        else:
            ui.notify(f'Deck "{target_name}" not found', color='negative')
        return

    settings_dialog = ui.dialog()
    confirm_close_dialog = ui.dialog()
    confirm_delete_dialog = ui.dialog()

    with confirm_close_dialog, ui.card().classes('rounded-xl p-5 bg-white shadow-xl w-72 gap-4'):
        ui.label('Do you want to save?').classes('text-base font-bold text-slate-800')
        with ui.row().classes('w-full justify-end gap-2'):
            ui.button('Discard', color='transparent',
                      on_click=lambda: [confirm_close_dialog.close(), settings_dialog.close()]).classes(
                'text-slate-500 font-medium shadow-none cursor-pointer')
            ui.button('Save', on_click=lambda: [confirm_close_dialog.close(), save_and_close_folder(settings_dialog)]).classes(
                'bg-slate-900 text-white font-semibold cursor-pointer')

    with confirm_delete_dialog, ui.card().classes('rounded-xl p-5 bg-white shadow-xl w-80 gap-4'):
        ui.label('Are you sure you want to delete this folder?').classes('text-base font-bold text-slate-800')
        with ui.row().classes('w-full justify-end gap-2'):
            ui.button('Cancel', color='transparent', on_click=confirm_delete_dialog.close).classes(
                'text-slate-500 font-medium shadow-none cursor-pointer')
            ui.button('Delete', on_click=lambda: execute_delete_folder(target_name, settings_dialog, confirm_delete_dialog)).classes(
                'bg-red-600 text-white font-semibold cursor-pointer')

    def request_close():
        if form_state['is_dirty']:
            confirm_close_dialog.open()
        else:
            settings_dialog.close()

    with settings_dialog, ui.card().classes('rounded-xl p-6 bg-white shadow-xl w-[560px] max-w-full gap-4'):
        with ui.row().classes('w-full items-center justify-between mb-1'):
            ui.label(f'Edit Folder: {target_name}').classes('text-lg font-bold text-slate-800')
            ui.button(icon='close', color='transparent', on_click=request_close).classes(
                'text-slate-400 hover:text-slate-700 p-0 min-h-0 min-w-0 shadow-none cursor-pointer'
            ).props('flat round dense size=sm')

        folder_inputs['name'] = ui.input('Folder Name', value=target_name).classes('w-full')
        folder_inputs['name'].on('update:model-value', lambda: form_state.update(is_dirty=True))

        with ui.row().classes('w-full justify-between items-center pt-2 border-t border-slate-100'):
            ui.button('DELETE FOLDER', color='transparent', on_click=confirm_delete_dialog.open).classes(
                'text-red-600 hover:text-red-700 font-normal text-sm shadow-none cursor-pointer px-0 py-1'
            ).props('flat')

            ui.button(icon='check', color='transparent', on_click=lambda: save_and_close_folder(settings_dialog)).classes(
                'text-emerald-600 hover:text-emerald-700 p-2 rounded-xl min-h-0 min-w-0 cursor-pointer shadow-none'
            ).props('flat round dense')

    def execute_delete_folder(target, s_dialog, d_dialog):
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute('SELECT id FROM folders WHERE name = ?', (target,))
        f_row = cursor.fetchone()
        if f_row:
            f_id = f_row['id']
            cursor.execute('SELECT id FROM decks WHERE folder_id = ?', (f_id,))
            for d in cursor.fetchall():
                cursor.execute('DELETE FROM cards WHERE deck_id = ?', (d['id'],))
            cursor.execute('DELETE FROM decks WHERE folder_id = ?', (f_id,))
            cursor.execute('DELETE FROM folders WHERE id = ?', (f_id,))
            if 'folder_states' in app.storage.user:
                f_states = dict(app.storage.user['folder_states'])
                if target in f_states:
                    del f_states[target]
                    app.storage.user['folder_states'] = f_states
        conn.commit()
        conn.close()
        d_dialog.close()
        s_dialog.close()
        if state.fn_sidebar_render_container:
            state.fn_sidebar_render_container()

    def save_and_close_folder(dialog):
        new_name = folder_inputs['name'].value.strip()
        if new_name:
            conn = get_db()
            cursor = conn.cursor()
            if target_name != new_name and 'folder_states' in app.storage.user:
                f_states = dict(app.storage.user['folder_states'])
                if target_name in f_states:
                    f_states[new_name] = f_states.pop(target_name)
                    app.storage.user['folder_states'] = f_states
            cursor.execute('UPDATE folders SET name = ? WHERE name = ?', (new_name, target_name))
            conn.commit()
            conn.close()
        dialog.close()
        if state.fn_sidebar_render_container:
            state.fn_sidebar_render_container()

    settings_dialog.open()


# --- DECK SETTINGS & CARD TABLE MODAL ---

def open_deck_editor_modal(deck_id: int):
    deck = get_deck_by_id(deck_id)
    if not deck:
        ui.notify('Deck not found!', color='warning')
        return

    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT value FROM settings WHERE key = ?', ('custom_study_mode',))
    c_row = cursor.fetchone()
    conn.close()
    is_custom_mode = (c_row and c_row['value'] == 'true')

    # declare all dialogs at top level
    options_dialog = ui.dialog().classes('w-full')
    confirm_delete_dialog = ui.dialog()
    advanced_edit_dialog = ui.dialog()

    edit_state = {'card': None, 'extra': {}}

    # 1. Delete confirmation ui
    with confirm_delete_dialog, ui.card().classes('rounded-xl p-5 bg-white shadow-xl w-80 gap-4'):
        ui.label(f'Are you sure you want to delete "{deck["name"]}"?').classes('text-base font-bold text-slate-800')
        with ui.row().classes('w-full justify-end gap-2'):
            ui.button('Cancel', color='transparent', on_click=confirm_delete_dialog.close).classes(
                'text-slate-500 font-medium shadow-none cursor-pointer')

            def execute_deck_delete():
                delete_deck(deck_id)
                confirm_delete_dialog.close()
                options_dialog.close()
                if app_state.get('current_deck') == deck['name']:
                    app_state['current_deck'] = 'Try creating a deck!'
                    if app_state.get('refresh_dashboard_cards'):
                        app_state['refresh_dashboard_cards']()
                if state.fn_sidebar_render_container:
                    state.fn_sidebar_render_container()
                ui.notify(f'Deck "{deck["name"]}" deleted.', color='negative')

            ui.button('Delete', on_click=execute_deck_delete).classes(
                'bg-red-600 text-white font-semibold cursor-pointer')

    # 2. Advanced edit dialog
    with advanced_edit_dialog, ui.card().classes('w-full max-w-3xl p-6 bg-white rounded-2xl shadow-2xl gap-4'):
        @ui.refreshable
        def render_advanced_form():
            if not edit_state['card']: return
            c = edit_state['card']
            extra = edit_state['extra']

            if 'custom_tabs' not in extra:
                word = c.get('word', '')
                meaning = c.get('meaning', '')
                tabs = []
                tabs.append({
                    'name': 'Meaning', 'layout': '2-column',
                    'left': [
                        {'title': 'OTHER MEANINGS', 'text': extra.get('other_meanings', extra.get('info', 'None'))},
                        {'title': 'WORD TYPE', 'text': extra.get('word_type', 'noun')}],
                    'right': [{'title': 'MEANING EXPLANATION',
                               'text': extra.get('meaning_explanation', f"Learn the meaning of '{word}': {meaning}.")}]
                })
                tabs.append({
                    'name': 'Reading', 'layout': '2-column',
                    'left': [{'title': 'READING', 'text': extra.get('reading', word), 'is_main_reading': True}],
                    'right': [{'title': 'READING EXPLANATION', 'text': extra.get('reading_explanation',
                                                                                 f"The reading for '{word}' is '{extra.get('reading', word)}'.")}]
                })

                comp_text = "\n".join(
                    [f"{i.get('character', '')} - {i.get('meaning', '')}" for i in extra.get('composition', [])])
                if comp_text.strip(): tabs.append({'name': 'Composition', 'layout': '1-column', 'single': [
                    {'title': 'KANJI COMPOSITION', 'text': comp_text.strip()}]})

                ctx_text = "\n\n".join(
                    [f"{s.get('ja', '')}\n{s.get('en', '')}" for s in extra.get('context_sentences', [])])
                if ctx_text.strip(): tabs.append({'name': 'Context', 'layout': '1-column',
                                                  'single': [{'title': 'CONTEXT SENTENCES', 'text': ctx_text.strip()}]})

                extra['custom_tabs'] = tabs

            tabs = extra['custom_tabs']

            with ui.row().classes('w-full justify-between items-center border-b border-slate-200 pb-2 mb-4'):
                ui.label(f'Customize Card Layout: {c.get("word", "")}').classes('text-xl font-bold text-slate-800')
                ui.button(icon='close', on_click=advanced_edit_dialog.close).props('flat round dense').classes(
                    'text-slate-400')

            with ui.column().classes('w-full gap-6 overflow-y-auto max-h-[60vh] pb-4 px-2'):
                for t_idx, tab in enumerate(tabs):
                    with ui.card().classes('w-full border border-slate-200 bg-slate-50 shadow-none p-4 gap-4'):
                        with ui.row().classes('w-full justify-between items-center'):
                            ui.input('Tab Name (e.g. Meaning)').bind_value(tab, 'name').classes('w-1/3').props(
                                'dense outlined')
                            ui.select(['1-column', '2-column'], label='Layout Style').bind_value(tab, 'layout').classes(
                                'w-1/3').props('dense outlined')
                            ui.button(icon='delete', color='negative',
                                      on_click=lambda i=t_idx: (tabs.pop(i), render_advanced_form.refresh())).props(
                                'flat round dense')

                        def render_fields(col_name, current_tab=tab):
                            fields = current_tab.setdefault(col_name, [])
                            for f_idx, field in enumerate(fields):
                                with ui.row().classes('w-full items-start gap-2 pl-4 py-2 border-l-2 border-slate-300'):
                                    ui.input('Section Title').bind_value(field, 'title').classes('w-1/3').props(
                                        'dense outlined')
                                    with ui.column().classes('flex-1 gap-0'):
                                        ui.textarea('Content Text').bind_value(field, 'text').classes('w-full').props(
                                            'dense outlined autogrow')
                                        ui.checkbox('Large Blue Text (For Main Readings)').bind_value(field,
                                                                                                      'is_main_reading').classes(
                                            'text-xs text-slate-500 mt-1')
                                    ui.button(icon='remove',
                                              on_click=lambda c_n=col_name, i=f_idx, t=current_tab: (t[c_n].pop(i),
                                                                                                     render_advanced_form.refresh())).props(
                                        'flat round dense size=sm')

                            ui.button(f'+ Add {col_name} section', on_click=lambda c_n=col_name, t=current_tab: (
                                t[c_n].append({'title': 'NEW SECTION', 'text': ''}),
                                render_advanced_form.refresh())).props('flat dense').classes('text-xs mt-2 ml-4')

                        if tab['layout'] == '2-column':
                            with ui.row().classes('w-full gap-4'):
                                with ui.column().classes('flex-1'):
                                    ui.label('Left Column Fields').classes(
                                        'font-bold text-xs text-slate-500 uppercase tracking-wider')
                                    render_fields('left')
                                with ui.column().classes('flex-1'):
                                    ui.label('Right Column Fields').classes(
                                        'font-bold text-xs text-slate-500 uppercase tracking-wider')
                                    render_fields('right')
                        else:
                            with ui.column().classes('w-full'):
                                ui.label('Single Column Fields').classes(
                                    'font-bold text-xs text-slate-500 uppercase tracking-wider')
                                render_fields('single')

                ui.button('+ ADD NEW TAB',
                          on_click=lambda: (tabs.append({'name': 'New Tab', 'layout': '1-column', 'single': []}),
                                            render_advanced_form.refresh())).classes(
                    'w-full bg-slate-200 text-slate-700 shadow-none font-bold')

            def save_advanced_data():
                conn = get_db()
                conn.cursor().execute('UPDATE cards SET extra_info = ? WHERE id = ?', (json.dumps(extra), c['id']))
                conn.commit()
                conn.close()
                ui.notify('Advanced layout saved!', type='positive')
                render_cards_list.refresh()
                advanced_edit_dialog.close()

            with ui.row().classes('w-full justify-end gap-2 pt-4 border-t border-slate-200 mt-2'):
                ui.button('Cancel', color='transparent', on_click=advanced_edit_dialog.close).classes(
                    'text-slate-500 font-medium shadow-none cursor-pointer')
                ui.button('Save Layout', on_click=save_advanced_data).classes(
                    'bg-slate-900 text-white font-semibold cursor-pointer')

        render_advanced_form()

    # 3. Main options dialog
    with options_dialog, ui.card().classes(
            'w-full max-w-4xl p-6 bg-white rounded-2xl shadow-2xl gap-6 max-h-[90vh] overflow-y-auto'):

        with ui.row().classes('w-full justify-between items-center border-b pb-4'):
            with ui.column().classes('gap-0'):
                # give the title a variable name so it can update live
                deck_title = ui.label(f'Deck Settings: {deck["name"]}').classes('text-2xl font-black text-slate-900')
                ui.label('Configure study targets and edit cards below.').classes('text-sm text-slate-500')

            with ui.row().classes('gap-4 items-center'):
                def handle_export():
                    import json, tempfile, os
                    conn = get_db()
                    cursor = conn.cursor()
                    cursor.execute("SELECT * FROM decks WHERE id = ?", (deck_id,))
                    d_row = dict(cursor.fetchone())

                    # grab cards n reset their progression to default values
                    cursor.execute("SELECT * FROM cards WHERE deck_id = ?", (deck_id,))
                    c_rows = []
                    for r in cursor.fetchall():
                        cd = dict(r)
                        cd['status'] = 'Unseen'
                        cd['srs_stage'] = 0
                        cd['learning_step'] = 0
                        cd['next_review_at'] = None
                        c_rows.append(cd)

                    cursor.execute("SELECT value FROM settings WHERE key = ?", (f'deck_{deck_id}_review_mode',))
                    m_row = cursor.fetchone()
                    conn.close()

                    export_data = {'deck': d_row, 'cards': c_rows, 'mode': m_row['value'] if m_row else 'Anki'}
                    filepath = os.path.join(tempfile.gettempdir(), f"{d_row['name']}.deck")
                    with open(filepath, 'w', encoding='utf-8') as f:
                        json.dump(export_data, f, ensure_ascii=False)

                    ui.download(filepath, f"{d_row['name']}.deck")
                    ui.notify(f'Exported {d_row["name"]} as a clean template!', type='positive')

                ui.button('EXPORT DECK', color='transparent', on_click=handle_export).classes(
                    'text-blue-500 hover:text-blue-700 font-bold text-xs shadow-none cursor-pointer tracking-wider'
                ).props('flat')

                ui.button('DELETE DECK', color='transparent', on_click=confirm_delete_dialog.open).classes(
                    'text-red-500 hover:text-red-700 font-bold text-xs shadow-none cursor-pointer tracking-wider'
                ).props('flat')
                ui.button(icon='close', on_click=options_dialog.close).props('flat round dense').classes(
                    'text-slate-400')

        # --- CONFIGURATION CARD ---
        with ui.card().classes('w-full p-4 bg-slate-50 border border-slate-200 rounded-xl gap-3'):
            ui.label('Deck Configuration').classes('text-xs font-bold text-slate-400 uppercase tracking-wider')
            with ui.row().classes('w-full items-center justify-between gap-4'):
                conn = get_db()
                m_cursor = conn.cursor()
                m_cursor.execute('SELECT value FROM settings WHERE key = ?', (f'deck_{deck_id}_review_mode',))
                mode_row = m_cursor.fetchone()
                conn.close()
                current_mode = mode_row['value'] if mode_row else 'Anki'

                name_input = ui.input('Deck Name', value=deck['name']).classes('w-48').props('dense')
                lessons_input = ui.number('Daily Lessons', value=deck.get('daily_lessons', 10), min=1, max=100).classes(
                    'w-28').props('dense')
                reviews_input = ui.number('Daily Reviews', value=deck.get('daily_reviews', 50), min=1, max=500).classes(
                    'w-28').props('dense')
                review_mode_select = ui.select(['Anki', 'Wani'], value=current_mode, label='Review Mode').classes(
                    'w-28').props('dense')

                def save_settings():
                    new_name = name_input.value.strip()
                    if not new_name:
                        ui.notify('Deck name cannot be empty!', color='negative')
                        return

                    # update name and limits in the decks table bc nothiNG is updating properly
                    conn_s = get_db()
                    cursor_s = conn_s.cursor()
                    cursor_s.execute('UPDATE decks SET name = ?, daily_lessons = ?, daily_reviews = ? WHERE id = ?',
                                     (new_name, int(lessons_input.value or 10), int(reviews_input.value or 50),
                                      deck_id))

                    cursor_s.execute(
                        'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
                        (f'deck_{deck_id}_review_mode', review_mode_select.value))
                    conn_s.commit()
                    conn_s.close()

                    # update live app state if this is the currently viewed deck
                    if app_state.get('current_deck') == deck['name']:
                        app_state['current_deck'] = new_name
                        if app_state.get('title_label'):
                            app_state['title_label'].text = new_name

                    # update the dialog title and deck dict asap
                    deck['name'] = new_name
                    deck_title.set_text(f'⚙️ Deck Settings: {new_name}')

                    ui.notify('Deck settings saved!', type='positive')

                    if app_state.get('refresh_lessons'):
                        app_state['refresh_lessons']()
                    if state.fn_sidebar_render_container:
                        state.fn_sidebar_render_container()

                ui.button('Save Settings', on_click=save_settings).classes(
                    'bg-slate-900 text-white font-semibold px-4 py-2 rounded-lg self-end mb-1 cursor-pointer'
                )

        with ui.column().classes('w-full gap-3 mt-2'):
            with ui.row().classes('w-full justify-between items-center'):
                ui.label('Flashcards List').classes('text-lg font-bold text-slate-800')

                def add_new_card():
                    save_card(
                        card_id=None, deck_id=deck_id, word='New Word', meaning='Meaning',
                        status='Unseen', srs_stage=0, learning_step=0, extra_info=json.dumps({'reading': ''})
                    )
                    render_cards_list.refresh()
                    ui.notify('New card added!', type='positive')

                ui.button('+ Add Card', on_click=add_new_card).classes(
                    'bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold px-3 py-2 rounded-lg shadow-sm cursor-pointer'
                )

            @ui.refreshable
            def render_cards_list():
                try:
                    current_cards = get_deck_cards(deck_id)
                    if not current_cards:
                        ui.label('No cards in this deck yet. Click "+ Add Card" above to create one.').classes(
                            'text-slate-400 italic py-6 text-center w-full'
                        )
                        return

                    with ui.row().style(
                            'display: grid; grid-template-columns: 3fr 3fr 2fr 2fr 2fr 140px; width: 100%; gap: 8px; padding: 10px; background-color: #f1f5f9; border-radius: 8px; font-weight: bold; font-size: 12px; color: #64748b; text-transform: uppercase; text-align: center;'):
                        ui.label('Front / Title').style('text-align: left; padding-left: 8px;')
                        ui.label('Back / Meaning').style('text-align: left; padding-left: 8px;')
                        ui.label('Status')
                        ui.label('Lrn Step')
                        ui.label('SRS Lvl')
                        ui.label('Actions')

                    for c in current_cards:
                        card_id = c['id']
                        c_word = c.get('word') or ''
                        c_meaning = c.get('meaning') or ''
                        raw_status = c.get('status')
                        if raw_status == 'Learned': raw_status = 'Graduated'

                        c_status = raw_status if raw_status in ['Unseen', 'Learning', 'Reviewing',
                                                                'Graduated'] else 'Unseen'
                        c_step = c.get('learning_step') if c.get('learning_step') is not None else 0
                        if c_step > 3: c_step = 3
                        c_srs = c.get('srs_stage') if c.get('srs_stage') is not None else 0
                        c_extra = c.get('extra_info') or '{}'

                        with ui.row().style(
                                'display: grid; grid-template-columns: 3fr 3fr 2fr 2fr 2fr 140px; width: 100%; gap: 8px; padding: 8px; border-bottom: 1px solid #f1f5f9; align-items: center;').classes(
                            'hover:bg-slate-50 transition-colors'):

                            w_input = ui.input(value=c_word).classes('w-full text-sm font-bold text-slate-800').props(
                                'dense outlined')
                            m_input = ui.input(value=c_meaning).classes('w-full text-sm text-slate-700').props(
                                'dense outlined')

                            status_select = ui.select(
                                options=['Unseen', 'Learning', 'Reviewing', 'Graduated'],
                                value=c_status
                            ).classes('w-full text-xs').props('dense outlined' + ('' if is_custom_mode else ' disable'))

                            step_select = ui.select(
                                options={0: 'Step 0', 1: 'Step 1', 2: 'Step 2', 3: 'Learned'},
                                value=c_step
                            ).classes('w-full text-xs').props('dense outlined' + ('' if is_custom_mode else ' disable'))

                            srs_select = ui.select(
                                options={
                                    0: 'Lvl 0 (Locked)', 1: 'Lvl 1 (App 1)', 2: 'Lvl 2 (App 2)', 3: 'Lvl 3 (App 3)',
                                    4: 'Lvl 4 (App 4)', 5: 'Lvl 5 (Guru 1)', 6: 'Lvl 6 (Guru 2)', 7: 'Lvl 7 (Master)',
                                    8: 'Lvl 8 (Enlight)', 9: 'Lvl 9 (Burned)'
                                },
                                value=c_srs
                            ).classes('w-full text-xs min-w-[120px]').props(
                                'dense outlined' + ('' if is_custom_mode else ' disable'))

                            def sync_and_save(cid=card_id, winp=w_input, minp=m_input, ssel=status_select,
                                              lstep=step_select, srs=srs_select, ext=c_extra):
                                if ssel.value in ['Unseen', 'Learning']: srs.value = 0
                                if ssel.value == 'Graduated': srs.value = 9
                                if srs.value == 9: ssel.value = 'Graduated'
                                if ssel.value in ['Reviewing', 'Graduated']:
                                    lstep.value = 3
                                elif ssel.value == 'Unseen':
                                    lstep.value = 0

                                save_card(cid, deck_id, winp.value, minp.value, ssel.value, srs.value, lstep.value, ext)
                                ui.notify(f'Saved "{winp.value}"', type='positive', color='slate-800')

                            def make_delete_handler(cid=card_id):
                                def handler():
                                    delete_card(cid)
                                    render_cards_list.refresh()
                                    ui.notify('Card removed', type='info')

                                return handler

                            def make_edit_handler(card_data=c, ext=c_extra):
                                def handler():
                                    try:
                                        parsed_ext = json.loads(ext)
                                    except:
                                        parsed_ext = {}
                                    edit_state['card'] = card_data
                                    edit_state['extra'] = parsed_ext
                                    render_advanced_form.refresh()
                                    advanced_edit_dialog.open()

                                return handler

                            # --- CLONE HANDLER ---
                            def make_clone_handler(card_data=c, ext=c_extra):
                                def handler():
                                    new_word = card_data.get('word', 'Word') + ' (Copy)'
                                    new_meaning = card_data.get('meaning', 'Meaning')
                                    # saves as new card with unseen status, but keeps all layout formatting n info
                                    save_card(card_id=None, deck_id=deck_id, word=new_word, meaning=new_meaning,
                                              status='Unseen', srs_stage=0, learning_step=0, extra_info=ext)
                                    render_cards_list.refresh()
                                    ui.notify('Card cloned successfully!', type='positive')

                                return handler

                            w_input.on('blur', sync_and_save)
                            m_input.on('blur', sync_and_save)
                            status_select.on('update:model-value', sync_and_save)
                            step_select.on('update:model-value', sync_and_save)
                            srs_select.on('update:model-value', sync_and_save)

                            with ui.row().classes('justify-center items-center gap-1'):
                                ui.button(icon='edit', on_click=make_edit_handler()).props(
                                    'flat round dense size=sm').classes('text-blue-500 hover:bg-blue-50')
                                ui.button(icon='content_copy', on_click=make_clone_handler()).props(
                                    'flat round dense size=sm').classes('text-emerald-500 hover:bg-emerald-50')
                                ui.button(icon='delete', color='negative', on_click=make_delete_handler()).props(
                                    'flat round dense size=sm')

                except Exception as e:
                    with ui.column().classes(
                            'w-full p-4 bg-red-50 border border-red-200 rounded-xl gap-2 items-center text-center'):
                        ui.label(f'⚠️ Warning: Could not load cards due to corrupted data.').classes(
                            'text-sm font-bold text-red-700')
                        ui.label(str(e)).classes('text-xs text-slate-500 font-mono')

                        def wipe_corrupted_cards():
                            conn_w = get_db()
                            conn_w.cursor().execute('DELETE FROM cards WHERE deck_id = ?', (deck_id,))
                            conn_w.commit()
                            conn_w.close()
                            render_cards_list.refresh()
                            ui.notify('Corrupted cards cleared!', type='positive')

                        ui.button('Clear All Cards in Deck', on_click=wipe_corrupted_cards).classes(
                            'bg-red-600 text-white text-xs font-bold px-3 py-1.5 rounded-lg cursor-pointer mt-1'
                        )

            render_cards_list()

    options_dialog.open()