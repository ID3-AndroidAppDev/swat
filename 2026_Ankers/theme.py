from nicegui import ui, app
from database import get_db, get_review_count, get_lesson_count
from state import app_state, deck_elements_pool, ACTIVE_STYLE, INACTIVE_STYLE
import state
from modals import open_settings, open_add_menu, open_main_settings

dragged_data = {'type': None, 'id': None}


def on_drag_start(item_type, item_id):
    dragged_data['type'] = item_type
    dragged_data['id'] = item_id


def handle_drop(target_type, target_id, position_mode):
    if not dragged_data['type'] or not dragged_data['id']:
        return

    conn = get_db()
    cursor = conn.cursor()

    d_type = dragged_data['type']
    d_id = dragged_data['id']

    def get_root_items():
        cursor.execute('''
            SELECT id, 'folder' as type, position FROM folders 
            UNION ALL 
            SELECT id, 'deck' as type, position FROM decks WHERE folder_id IS NULL 
            ORDER BY position ASC, id ASC
        ''')
        return [{'id': r['id'], 'type': r['type']} for r in cursor.fetchall()]

    root_item_ids = [item['id'] for item in get_root_items()]
    is_target_root = (target_type == 'folder') or (target_id in root_item_ids and target_type == 'deck')

    if d_type == 'deck':
        if target_type == 'folder' and position_mode == 'inside':
            cursor.execute('UPDATE decks SET folder_id = ? WHERE id = ?', (target_id, d_id))
            cursor.execute('SELECT id FROM decks WHERE folder_id = ? AND id != ? ORDER BY position ASC',
                           (target_id, d_id))
            sibling_ids = [r['id'] for r in cursor.fetchall()]
            sibling_ids.append(d_id)
            for idx, s_id in enumerate(sibling_ids):
                cursor.execute('UPDATE decks SET position = ? WHERE id = ?', (idx, s_id))
        elif is_target_root:
            cursor.execute('UPDATE decks SET folder_id = NULL WHERE id = ?', (d_id,))
            root_items = get_root_items()
            root_items = [item for item in root_items if not (item['type'] == 'deck' and item['id'] == d_id)]

            target_idx = None
            for idx, item in enumerate(root_items):
                if item['type'] == target_type and item['id'] == target_id:
                    target_idx = idx
                    break

            new_deck_item = {'id': d_id, 'type': 'deck'}
            if target_idx is not None:
                if position_mode == 'above':
                    root_items.insert(target_idx, new_deck_item)
                else:
                    root_items.insert(target_idx + 1, new_deck_item)
            else:
                root_items.append(new_deck_item)

            for idx, item in enumerate(root_items):
                if item['type'] == 'folder':
                    cursor.execute('UPDATE folders SET position = ? WHERE id = ?', (idx, item['id']))
                else:
                    cursor.execute('UPDATE decks SET folder_id = NULL, position = ? WHERE id = ?',
                                   (idx, item['id']))
        else:
            target_folder_id = None
            if target_type == 'deck':
                cursor.execute('SELECT folder_id FROM decks WHERE id = ?', (target_id,))
                t_row = cursor.fetchone()
                target_folder_id = t_row['folder_id'] if t_row else None

            if target_folder_id is not None:
                cursor.execute('UPDATE decks SET folder_id = ? WHERE id = ?', (target_folder_id, d_id))
                cursor.execute('SELECT id FROM decks WHERE folder_id = ? AND id != ? ORDER BY position ASC',
                               (target_folder_id, d_id))
                deck_ids = [r['id'] for r in cursor.fetchall()]
                if target_id in deck_ids:
                    idx = deck_ids.index(target_id)
                    if position_mode == 'above':
                        deck_ids.insert(idx, d_id)
                    else:
                        deck_ids.insert(idx + 1, d_id)
                else:
                    deck_ids.append(d_id)
                for idx, deck_id in enumerate(deck_ids):
                    cursor.execute('UPDATE decks SET position = ? WHERE id = ?', (idx, deck_id))

    elif d_type == 'folder':
        root_items = get_root_items()
        root_items = [item for item in root_items if not (item['type'] == 'folder' and item['id'] == d_id)]

        target_idx = None
        for idx, item in enumerate(root_items):
            if item['type'] == target_type and item['id'] == target_id:
                target_idx = idx
                break

        new_folder_item = {'id': d_id, 'type': 'folder'}
        if target_idx is not None:
            if position_mode == 'above':
                root_items.insert(target_idx, new_folder_item)
            else:
                root_items.insert(target_idx + 1, new_folder_item)
        else:
            root_items.append(new_folder_item)

        for idx, item in enumerate(root_items):
            if item['type'] == 'folder':
                cursor.execute('UPDATE folders SET position = ? WHERE id = ?', (idx, item['id']))
            else:
                cursor.execute('UPDATE decks SET position = ? WHERE id = ?', (idx, item['id']))

    conn.commit()
    conn.close()

    dragged_data['type'] = None
    dragged_data['id'] = None

    if state.fn_sidebar_render_container:
        state.fn_sidebar_render_container()


def handle_drop_root():
    if dragged_data['type'] == 'deck':
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute('SELECT MAX(position) as max_pos FROM decks WHERE folder_id IS NULL')
        row = cursor.fetchone()
        next_pos = (row['max_pos'] + 1) if row and row['max_pos'] is not None else 0
        cursor.execute('UPDATE decks SET folder_id = NULL, position = ? WHERE id = ?', (next_pos, dragged_data['id']))
        conn.commit()
        conn.close()
        dragged_data['type'] = None
        dragged_data['id'] = None
        if state.fn_sidebar_render_container:
            state.fn_sidebar_render_container()


def frame(title: str):
    ui.add_head_html('<link rel="stylesheet" href="/static/style.css">')
    ui.query('body').classes('bg-slate-50 text-slate-800 antialiased')

    # register updating of lessons n reviews hook for settings n modals
    def refresh_lessons_ui():
        if app_state.get('refresh_dashboard_cards'):
            app_state['refresh_dashboard_cards']()

        if state.fn_sidebar_render_container:
            state.fn_sidebar_render_container()

    app_state['refresh_lessons'] = refresh_lessons_ui

    # Responsive header
    with ui.header().classes(
            'bg-slate-100 text-slate-800 border-b border-slate-200 p-3 md:hidden flex items-center justify-between'):
        ui.button(icon='menu', color='transparent', on_click=lambda: sidebar.toggle()).props(
            'flat round dense').classes('text-slate-700 bg-slate-200/80 hover:bg-slate-300 p-2')
        ui.label('ANKERS').classes('font-black tracking-wider text-slate-700 text-lg')
        ui.element('div').classes('w-8')

    # Left sidebar
    with ui.left_drawer(value=True, fixed=True).props('breakpoint=768') as sidebar:
        sidebar.classes(
            'bg-slate-100 p-6 flex flex-col justify-between border-r border-slate-200 h-full overflow-hidden w-84')

        with ui.column().classes('w-full items-start mb-4'):
            ui.label('ANKERS').classes('text-3xl font-black tracking-wider text-slate-800 pl-2 cursor-pointer').on(
                'click', lambda: ui.navigate.to('/'))

        sidebar_decks_container = ui.column().classes(
            'w-full flex-1 overflow-y-auto pr-1 items-center transition-colors rounded-xl')
        sidebar_decks_container.props(
            'ondragover="event.preventDefault()"'
            'ondragenter="event.currentTarget.classList.add(\'bg-blue-50/65\'); event.preventDefault()"'
            'ondragleave="event.currentTarget.classList.remove(\'bg-blue-50/65\')"'
            'ondrop="event.currentTarget.classList.remove(\'bg-blue-50/60\')"'
        )
        sidebar_decks_container.on('drop', handle_drop_root)

        def select_deck(target_name, row_element):
            app_state['current_deck'] = target_name

            if app_state.get('title_label'):
                app_state['title_label'].text = target_name

            sub_decks = []
            conn = get_db()
            cursor = conn.cursor()
            cursor.execute('SELECT d.name FROM decks d JOIN folders f ON d.folder_id = f.id')
            for row in cursor.fetchall():
                sub_decks.append(row['name'])
            conn.close()

            for element in deck_elements_pool:
                is_sub = element.context_tag in sub_decks
                pad_cls = 'py-1 pl-4 pr-2' if is_sub else 'py-2 px-2'
                element.classes(
                    replace=f'deck-container grid grid-cols-[1fr_auto] items-center w-full rounded-lg cursor-pointer border {INACTIVE_STYLE} {pad_cls} group')

            is_target_sub = target_name in sub_decks
            target_pad = 'py-1 pl-4 pr-2' if is_target_sub else 'py-2 px-2'
            row_element.classes(
                replace=f'deck-container grid grid-cols-[1fr_auto] items-center w-full rounded-lg cursor-pointer border {ACTIVE_STYLE} {target_pad} group')

            # instantly refresh the dashboard cards and their buttons
            if app_state.get('refresh_dashboard_cards'):
                app_state['refresh_dashboard_cards']()
            if app_state.get('refresh_heatmap'):
                app_state['refresh_heatmap']()

        def render_sidebar_decks():
            sidebar_decks_container.clear()
            deck_elements_pool.clear()

            conn = get_db()
            cursor = conn.cursor()

            with sidebar_decks_container:
                ui.space()
                with ui.column().classes('max-w-[260px] w-full gap-1 items-start justify-start pt-2'):
                    with ui.row().classes('w-full items-center gap-2 mb-2 no-wrap'):
                        ui.label('Decks').classes('text-base font-extrabold text-slate-700 tracking-wide')
                        ui.element('div').classes('flex-1 h-[1px] bg-slate-400')

                    cursor.execute('''
                        SELECT id, 'folder' as type, name, position FROM folders 
                        UNION ALL 
                        SELECT id, 'deck' as type, name, position FROM decks WHERE folder_id IS NULL 
                        ORDER BY position ASC, id ASC
                    ''')
                    root_items = cursor.fetchall()

                    folder_states = app.storage.user.get('folder_states', {})

                    def render_gap(t_type, t_id, p_mode):
                        with ui.element('div').classes(
                                'w-full h-2 -my-1 cursor-pointer relative flex items-center z-10') as gap:
                            ui.element('div').classes(
                                'absolute inset-x-0 h-0.5 bg-transparent transition-all pointer-events-none drop-line')
                            gap.props(
                                'ondragover="event.preventDefault()"'
                                'ondragenter="let line = event.currentTarget.querySelector(\'.drop-line\'); line.classList.remove(\'bg-transparent\'); line.classList.add(\'bg-blue-500\', \'h-1\'); event.preventDefault()"'
                                'ondragleave="let line = event.currentTarget.querySelector(\'.drop-line\'); line.classList.remove(\'bg-blue-500\', \'h-1\'); line.classList.add(\'bg-transparent\');"'
                                'ondrop="let line = event.currentTarget.querySelector(\'.drop-line\'); line.classList.remove(\'bg-blue-500\', \'h-1\'); line.classList.add(\'bg-transparent\');"'
                            )
                            gap.on('drop', lambda _: handle_drop(t_type, t_id, p_mode))

                    for r_item in root_items:
                        item_id = r_item['id']
                        item_type = r_item['type']
                        item_name = r_item['name']

                        render_gap(item_type, item_id, 'above')

                        if item_type == 'folder':
                            cursor.execute('SELECT * FROM decks WHERE folder_id = ? ORDER BY position ASC, id ASC',
                                           (item_id,))
                            f_decks = cursor.fetchall()

                            # calculate sums for the folder
                            total_l = 0
                            total_r = 0
                            for d in f_decks:
                                l_avail = get_lesson_count(d['id'])
                                total_l += min(l_avail, d['daily_lessons'])
                                total_r += get_review_count(d['id'])

                            is_open = folder_states.get(item_name, False)

                            with ui.element('div').classes(
                                    'deck-container grid grid-cols-[1fr_auto] items-center w-full py-1.5 px-2 rounded-lg cursor-pointer border border-transparent hover:bg-slate-200 text-slate-700 group transition-colors') as f_row:
                                f_row.props(
                                    'draggable="true" '
                                    'ondragstart="event.dataTransfer.setData(\'text/plain\', \'\');" '
                                    'ondragover="event.preventDefault()" '
                                    'ondragenter="event.currentTarget.classList.add(\'bg-blue-100\', \'border-blue-400\'); event.preventDefault()" '
                                    'ondragleave="event.currentTarget.classList.remove(\'bg-blue-100\', \'border-blue-400\')" '
                                    'ondrop="event.currentTarget.classList.remove(\'bg-blue-100\', \'border-blue-400\')"'
                                )
                                f_row.on('dragstart', lambda _, fid=item_id: on_drag_start('folder', fid))
                                f_row.on('drop', lambda _, fid=item_id: handle_drop('folder', fid, 'inside'))

                                f_row_click_target = ui.row().classes(
                                    'items-center gap-2 no-wrap overflow-hidden cursor-pointer flex-1')
                                with f_row_click_target:
                                    with ui.element('div').classes('w-5 shrink-0 flex items-center justify-center'):
                                        f_arrow_open = ui.icon('arrow_drop_down', size='sm')
                                        if not is_open:
                                            f_arrow_open.classes('hidden')
                                        f_arrow_closed = ui.icon('arrow_right', size='sm')
                                        if is_open:
                                            f_arrow_closed.classes('hidden')
                                    ui.label(item_name).classes('text-base font-normal text-slate-700 truncate')

                                with ui.row().classes('items-center justify-end w-[72px] shrink-0 relative h-6'):
                                    f_badges = ui.row().classes(
                                        'items-center gap-1.5 absolute right-0 transition-opacity duration-150 group-hover:opacity-0')
                                    if is_open:
                                        f_badges.classes('hidden')
                                    with f_badges:
                                        ui.label(str(total_l)).classes(
                                            'text-xs font-bold px-2 py-0.5 rounded-full border border-red-300 text-red-500 bg-transparent')
                                        ui.label(str(total_r)).classes(
                                            'text-xs font-bold px-2 py-0.5 rounded-full border border-blue-300 text-blue-500 bg-transparent')
                                    ui.button(icon='edit', color='transparent',
                                              on_click=lambda fn=item_name: open_settings(fn, 'folder')).props(
                                        'flat round dense size=sm').classes(
                                        'text-slate-400 hover:text-slate-700 p-0 min-h-0 min-w-0 shadow-none cursor-pointer absolute right-0 opacity-0 group-hover:opacity-100 transition-opacity duration-150')

                            f_content_cls = 'w-full gap-1 pl-2 pt-1' if is_open else 'w-full gap-1 pl-2 pt-1 hidden'
                            f_content = ui.column().classes(f_content_cls)

                            def make_folder_toggle(name, ao, ac, co, bd):
                                def toggle():
                                    f_states = dict(app.storage.user.get('folder_states', {}))
                                    current = f_states.get(name, False)
                                    new_val = not current
                                    f_states[name] = new_val
                                    app.storage.user['folder_states'] = f_states

                                    ao.classes(toggle='hidden')
                                    ac.classes(toggle='hidden')
                                    co.classes(toggle='hidden')
                                    bd.classes(toggle='hidden')

                                return toggle

                            f_row_click_target.on('click',
                                                  make_folder_toggle(item_name, f_arrow_open, f_arrow_closed, f_content,
                                                                     f_badges))

                            with f_content:
                                for sub_d in f_decks:
                                    sd_name = sub_d['name']
                                    sd_id = sub_d['id']

                                    # fetch actual counts
                                    sd_limit = sub_d['daily_lessons']
                                    sd_avail = get_lesson_count(sd_id)
                                    sd_lessons = min(sd_avail, sd_limit)

                                    sd_reviews = get_review_count(sd_id)
                                    is_active = (app_state['current_deck'] == sd_name)
                                    active_cls = ACTIVE_STYLE if is_active else INACTIVE_STYLE

                                    render_gap('deck', sd_id, 'above')

                                    with ui.element('div').classes(
                                            f'deck-container grid grid-cols-[1fr_auto] items-center w-full py-1 pl-4 pr-2 rounded-lg cursor-pointer border {active_cls} group transition-all') as sd_row:
                                        sd_row.props(
                                            'draggable="true" '
                                            'ondragstart="event.dataTransfer.setData(\'text/plain\', \'\');" '
                                            'ondragover="event.preventDefault()" '
                                            'ondragenter="event.currentTarget.classList.add(\'bg-blue-50/50\'); event.preventDefault()" '
                                            'ondragleave="event.currentTarget.classList.remove(\'bg-blue-50/50\')" '
                                            'ondrop="event.currentTarget.classList.remove(\'bg-blue-50/50\')"'
                                        )
                                        sd_row.on('dragstart', lambda _, did=sd_id: on_drag_start('deck', did))
                                        sd_row.on('drop', lambda _, did=sd_id: handle_drop('deck', did, 'below'))

                                        with ui.row().classes('items-center gap-2 no-wrap overflow-hidden flex-1'):
                                            with ui.element('div').classes(
                                                    'w-5 shrink-0 flex items-center justify-center'):
                                                ui.icon('fiber_manual_record', size='6px').classes('text-slate-400')
                                            ui.label(sd_name).classes('text-base font-normal truncate')
                                        with ui.row().classes(
                                                'items-center justify-end w-[72px] shrink-0 relative h-6'):
                                            with ui.row().classes(
                                                    'items-center gap-1.5 absolute right-0 transition-opacity duration-150 group-hover:opacity-0'):
                                                ui.label(str(sd_lessons)).classes(
                                                    'text-xs font-bold px-2 py-0.5 rounded-full border border-red-300 text-red-500 bg-transparent')
                                                ui.label(str(sd_reviews)).classes(
                                                    'text-xs font-bold px-2 py-0.5 rounded-full border border-blue-300 text-blue-500 bg-transparent')
                                            ui.button(icon='settings', color='transparent',
                                                      on_click=lambda name=sd_name: open_settings(name, 'deck')).props(
                                                'flat round dense size=sm').classes(
                                                'text-slate-400 hover:text-slate-700 p-0 min-h-0 min-w-0 shadow-none cursor-pointer absolute right-0 opacity-0 group-hover:opacity-100 transition-opacity duration-150')

                                    sd_row.on('click', lambda name=sd_name, r=sd_row: select_deck(name, r))
                                    sd_row.context_tag = sd_name
                                    deck_elements_pool.append(sd_row)

                                if f_decks:
                                    render_gap('deck', f_decks[-1]['id'], 'below')

                        else:
                            rd_name = item_name
                            rd_id = item_id
                            cursor.execute('SELECT daily_lessons FROM decks WHERE id = ?', (rd_id,))
                            rd_row_data = cursor.fetchone()

                            # fetch actual counts
                            rd_limit = rd_row_data['daily_lessons'] if rd_row_data else 15
                            rd_avail = get_lesson_count(rd_id)
                            rd_lessons = min(rd_avail, rd_limit)

                            rd_reviews = get_review_count(rd_id)
                            is_active = (app_state['current_deck'] == rd_name)
                            active_cls = ACTIVE_STYLE if is_active else INACTIVE_STYLE

                            with ui.element('div').classes(
                                    f'deck-container grid grid-cols-[1fr_auto] items-center w-full py-2 px-2 rounded-lg cursor-pointer border {active_cls} group transition-all') as rd_row:
                                rd_row.props(
                                    'draggable="true" '
                                    'ondragstart="event.dataTransfer.setData(\'text/plain\', \'\');" '
                                    'ondragover="event.preventDefault()" '
                                    'ondragenter="event.currentTarget.classList.add(\'bg-blue-50/50\'); event.preventDefault()" '
                                    'ondragleave="event.currentTarget.classList.remove(\'bg-blue-50/50\')" '
                                    'ondrop="event.currentTarget.classList.remove(\'bg-blue-50/50\')"'
                                )
                                rd_row.on('dragstart', lambda _, did=rd_id: on_drag_start('deck', did))
                                rd_row.on('drop', lambda _, did=rd_id: handle_drop('deck', did, 'below'))

                                with ui.row().classes('items-center gap-2 no-wrap overflow-hidden flex-1'):
                                    with ui.element('div').classes('w-5 shrink-0 flex items-center justify-center'):
                                        ui.icon('fiber_manual_record', size='8px').classes('text-slate-700')
                                    ui.label(rd_name).classes('text-base font-normal truncate')
                                with ui.row().classes('items-center justify-end w-[72px] shrink-0 relative h-6'):
                                    with ui.row().classes(
                                            'items-center gap-1.5 absolute right-0 transition-opacity duration-150 group-hover:opacity-0'):
                                        ui.label(str(rd_lessons)).classes(
                                            'text-xs font-bold px-2 py-0.5 rounded-full border border-red-300 text-red-500 bg-transparent')
                                        ui.label(str(rd_reviews)).classes(
                                            'text-xs font-bold px-2 py-0.5 rounded-full border border-blue-300 text-blue-500 bg-transparent')
                                    ui.button(icon='settings', color='transparent',
                                              on_click=lambda name=rd_name: open_settings(name, 'deck')).props(
                                        'flat round dense size=sm').classes(
                                        'text-slate-400 hover:text-slate-700 p-0 min-h-0 min-w-0 shadow-none cursor-pointer absolute right-0 opacity-0 group-hover:opacity-100 transition-opacity duration-150')

                            rd_row.on('click', lambda name=rd_name, r=rd_row: select_deck(name, r))
                            rd_row.context_tag = rd_name
                            deck_elements_pool.append(rd_row)

                    if root_items:
                        render_gap(root_items[-1]['type'], root_items[-1]['id'], 'below')

                        # --- NEW ADD/IMPORT DIALOG ---
                    add_or_import_dialog = ui.dialog()
                    with add_or_import_dialog, ui.card().classes(
                            'rounded-2xl p-6 bg-white shadow-xl w-80 gap-4 flex flex-col items-center'):
                        ui.label('Add to Library').classes('text-lg font-bold text-slate-800 self-start')

                        ui.button('Create New Deck / Folder',
                                  on_click=lambda: (add_or_import_dialog.close(), open_add_menu())).classes(
                            'w-full bg-slate-900 text-white font-semibold cursor-pointer rounded-xl py-2 shadow-sm'
                        )

                        ui.element('div').classes('w-full h-[1px] bg-slate-200 my-2')
                        ui.label('OR IMPORT DECK').classes(
                            'text-[11px] font-bold text-slate-400 uppercase tracking-wider self-start')

                        # 2. draggable import logic stuf
                        async def handle_deck_import(e):
                            import json
                            try:
                                content = await e.file.text()
                                data = json.loads(content)
                                deck_info = data['deck']
                                cards_info = data['cards']
                                mode_info = data.get('mode', 'Anki')

                                conn = get_db()
                                cursor = conn.cursor()

                                # check if deck w this name already exists, and append suffix if it does
                                base_name = deck_info['name']
                                deck_name = base_name
                                counter = 1
                                while True:
                                    cursor.execute('SELECT id FROM decks WHERE name = ?', (deck_name,))
                                    if not cursor.fetchone():
                                        break
                                    counter += 1
                                    deck_name = f"{base_name} ({counter})"

                                cursor.execute('SELECT MAX(position) as max_pos FROM decks WHERE folder_id IS NULL')
                                row = cursor.fetchone()
                                next_pos = (row['max_pos'] + 1) if row and row['max_pos'] is not None else 0

                                cursor.execute(
                                    'INSERT INTO decks (name, daily_lessons, daily_reviews, position) VALUES (?, ?, ?, ?)',
                                    (deck_name, deck_info.get('daily_lessons', 15), deck_info.get('daily_reviews', 50),
                                     next_pos))
                                new_deck_id = cursor.lastrowid

                                cursor.execute(
                                    'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
                                    (f'deck_{new_deck_id}_review_mode', mode_info))

                                for c in cards_info:
                                    cursor.execute('''
                                        INSERT INTO cards (deck_id, word, meaning, status, srs_stage, learning_step, next_review_at, extra_info)
                                        VALUES (?, ?, ?, 'Unseen', 0, 0, NULL, ?)
                                    ''', (new_deck_id, c['word'], c['meaning'], c['extra_info']))

                                conn.commit()
                                conn.close()

                                ui.notify(f'Successfully imported: {deck_name}!', type='positive')
                                await add_or_import_dialog.close()

                                if state.fn_sidebar_render_container:
                                    state.fn_sidebar_render_container()

                            except Exception as ex:
                                ui.notify(f'Import failed: {str(ex)}', color='negative')

                        # Upload dropzone
                        ui.upload(on_upload=handle_deck_import, auto_upload=True, label='Drop .deck file or click',
                                  multiple=False).props(
                            'flat accept=".deck" color="transparent" text-color="slate-500"'
                        ).classes(
                            'w-full shadow-none border-2 border-dashed border-slate-300 rounded-xl hover:bg-slate-100 transition-colors cursor-pointer')

                    ui.button('+ add more', color='transparent', on_click=add_or_import_dialog.open).props(
                        'flat dense').classes(
                        'text-xs mt-6 mx-auto w-48 py-2 font-normal tracking-widest text-slate-500/80 lowercase rounded-full sketch-dotted-btn block cursor-pointer')

                ui.space()

                with ui.row().classes(
                        'w-full gap-1 pt-4 border-t border-slate-200 items-center justify-start bg-slate-100 pl-2 pr-2'):
                    ui.button(icon='settings', color='transparent',
                              on_click=open_main_settings).classes(
                        'text-slate-600 hover:text-slate-900 p-2 min-h-0 min-w-0 shadow-none cursor-pointer').props(
                        'flat round dense size=md')

            # apply stored theme settings on load
            conn = get_db()
            cursor = conn.cursor()
            cursor.execute('SELECT value FROM settings WHERE key = ?', ('dark_mode',))
            d_row = cursor.fetchone()
            conn.close()
            if d_row and d_row['value'] == 'true':
                ui.run_javascript('document.body.classList.add("dark")')

        state.fn_sidebar_render_container = render_sidebar_decks
        render_sidebar_decks()

    content_container = ui.column().classes(
        'flex-1 px-4 md:px-12 pt-20 md:pt-30 pb-12 gap-8 items-center justify-start w-full h-screen overflow-y-auto')
    return content_container