from datetime import date, timedelta
from nicegui import ui

import state
from database import (
    calculate_streaks, get_current_week_status, get_db,
    get_review_count_by_name, get_month_activity, get_year_activity,
    get_lesson_count_by_name, get_deck_by_name, get_activity_from_db
)
from state import app_state
from modals import open_settings
import theme


@ui.page('/')
def home_page():
    today_ref = date.today()

    # --- AUTO-SELECT LOGIC ---
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT name FROM decks ORDER BY position ASC, id ASC LIMIT 1')
    first_deck = cursor.fetchone()
    conn.close()

    if first_deck:
        if app_state['current_deck'] in ['Try creating a deck!', 'Physics Stuff']:
            app_state['current_deck'] = first_deck['name']
    else:
        app_state['current_deck'] = 'Try creating a deck!'

    content = theme.frame('Home')

    with content:
        with ui.column().classes('w-full max-w-6xl gap-8 items-center'):

            # --- DECK HEADER WITH SETTINGS ICON ---
            with ui.row().classes('w-full justify-between items-center'):
                dashboard_title = ui.label(app_state['current_deck']).classes(
                    'text-3xl font-extrabold text-slate-900 tracking-tight self-start')
                app_state['title_label'] = dashboard_title

                def launch_deck_settings():
                    deck = get_deck_by_name(app_state['current_deck'])
                    if deck:
                        open_settings(deck['name'], 'deck')
                    else:
                        ui.notify('Please create a deck first using the sidebar!', type='warning')

                # --- TEMPORARY TEST BUTTON TO SKIP TIME ---
                def simulate_next_day():
                    conn = get_db()
                    cursor = conn.cursor()
                    cursor.execute("""
                        UPDATE cards 
                        SET next_review_at = datetime(next_review_at, '-1 day')
                        WHERE next_review_at IS NOT NULL
                    """)
                    conn.commit()
                    conn.close()
                    ui.notify('Advanced time by 1 day! Reviews are now due.', type='positive')

                    # Refresh the main dashboard cards
                    if app_state.get('refresh_dashboard_cards'):
                        app_state['refresh_dashboard_cards']()

                    # Refresh the tiny sidebar circles!
                    if state.fn_sidebar_render_container:
                        state.fn_sidebar_render_container()

                with ui.row().classes('items-center gap-2'):
                    ui.button('+1 Day (Test)', on_click=simulate_next_day).classes(
                        'bg-amber-100 hover:bg-amber-200 text-amber-800 text-xs font-bold px-3 py-1.5 rounded-lg shadow-sm cursor-pointer'
                    )
                    ui.button(icon='settings', on_click=launch_deck_settings).classes(
                        'text-slate-400 hover:text-slate-800 shadow-none cursor-pointer'
                    ).props('flat round dense size=md')

            # --- DASHBOARD CARDS ---
            @ui.refreshable
            def render_dashboard_cards():
                import datetime

                conn = get_db()
                cursor = conn.cursor()
                cursor.execute('SELECT id, daily_lessons FROM decks WHERE name = ?',
                               (app_state['current_deck'],))
                deck_row = cursor.fetchone()

                deck_id = deck_row['id'] if deck_row else 0
                daily_limit = deck_row['daily_lessons'] if deck_row else 15

                today_str = datetime.date.today().isoformat()

                # use SUM() to add up all the logs for today
                cursor.execute('SELECT SUM(lessons) as total_lessons FROM study_logs WHERE deck_id = ? AND date = ?',
                               (deck_id, today_str))
                log_row = cursor.fetchone()

                # get total learned cards for extra study mode
                cursor.execute(
                    "SELECT count(*) as total FROM cards WHERE deck_id = ? AND status IN ('Reviewing', 'Graduated')",
                    (deck_id,))
                total_reviewable = cursor.fetchone()['total']
                conn.close()

                # extract the sum, default to 0 if no logs exist yet
                lessons_done_today = log_row['total_lessons'] if (
                            log_row and log_row['total_lessons'] is not None) else 0
                available_lessons = get_lesson_count_by_name(app_state['current_deck'])
                remaining_quota = max(0, daily_limit - lessons_done_today)
                display_lessons = min(available_lessons, remaining_quota)
                initial_reviews = get_review_count_by_name(app_state['current_deck'])

                # --- CUSTOM LESSON LIMIT MODAL ---
                lesson_options_dialog = ui.dialog()
                with lesson_options_dialog, ui.card().classes('rounded-2xl p-6 bg-white shadow-xl w-80 gap-4'):
                    ui.label('Custom Lesson Session').classes('text-lg font-bold text-slate-800')
                    ui.label(f'Available lessons: {available_lessons}').classes('text-sm text-slate-500 -mt-3')

                    default_custom_val = display_lessons if display_lessons > 0 else min(available_lessons, 5)
                    custom_limit = ui.number('Number of lessons', value=default_custom_val, min=1,
                                             max=max(1, available_lessons), format='%.0f').classes('w-full')

                    def start_custom_lessons():
                        if available_lessons <= 0:
                            ui.notify("There are no more lessons for you!", type='warning')
                            lesson_options_dialog.close()
                            return
                        limit_val = int(custom_limit.value) if custom_limit.value else default_custom_val
                        if limit_val > available_lessons:
                            limit_val = available_lessons
                            ui.notify(f'Capped to {available_lessons} available lessons.', type='info')
                        ui.navigate.to(f'/study/{deck_id}?limit={limit_val}')

                    custom_limit.on('keydown.enter', start_custom_lessons)

                    with ui.row().classes('w-full justify-end gap-2 pt-2'):
                        ui.button('Cancel', color='transparent', on_click=lesson_options_dialog.close).classes(
                            'text-slate-500 font-medium shadow-none cursor-pointer')
                        ui.button('Start', on_click=start_custom_lessons).classes(
                            'bg-slate-900 text-white font-semibold cursor-pointer')

                # --- EXTRA REVIEW MODAL ---
                review_options_dialog = ui.dialog()
                with review_options_dialog, ui.card().classes('rounded-2xl p-6 bg-white shadow-xl w-80 gap-4'):
                    ui.label('Extra Study Session').classes('text-lg font-bold text-slate-800')
                    ui.label(
                        'This is extra practice. It will NOT affect your real card progression or SRS levels.').classes(
                        'text-xs text-blue-500 font-medium -mt-2')
                    ui.label(f'Available cards: {total_reviewable}').classes('text-sm text-slate-500 -mt-2')

                    default_rev_val = min(total_reviewable, 10)
                    extra_limit = ui.number('Number of cards', value=default_rev_val, min=1,
                                            max=max(1, total_reviewable), format='%.0f').classes('w-full')

                    def start_extra_review():
                        if total_reviewable <= 0:
                            ui.notify("You don't have any learned cards to practice yet!", type='warning')
                            review_options_dialog.close()
                            return
                        limit_val = int(extra_limit.value) if extra_limit.value else default_rev_val
                        if limit_val > total_reviewable:
                            limit_val = total_reviewable
                            ui.notify(f'Capped to {total_reviewable} available cards.', type='info')

                        ui.navigate.to(f'/review/{deck_id}?limit={limit_val}&extra=true')

                    extra_limit.on('keydown.enter', start_extra_review)

                    with ui.row().classes('w-full justify-end gap-2 pt-2'):
                        ui.button('Cancel', color='transparent', on_click=review_options_dialog.close).classes(
                            'text-slate-500 font-medium shadow-none cursor-pointer')
                        ui.button('Start', on_click=start_extra_review).classes(
                            'bg-slate-900 text-white font-semibold cursor-pointer')

                with ui.row().classes('w-full gap-6 items-stretch grid grid-cols-1 md:grid-cols-[3fr_3fr_2fr]'):

                    def action_card(title, count, icon_name, btn_text='Start >', route='/',
                                    on_options_click=None, total_available=None):
                        with ui.column().classes(
                                'p-6 bg-white border border-slate-200 rounded-2xl gap-4 flex-1 shadow-sm hover:shadow-md transition-shadow justify-between items-start text-left'):
                            with ui.row().classes('w-full gap-4 items-center no-wrap'):
                                with ui.column().classes(
                                        'w-24 h-24 bg-slate-100 border border-slate-200 rounded-xl items-center justify-center shrink-0'):
                                    ui.icon(icon_name, size='2rem').classes('text-slate-400')
                                with ui.column().classes('gap-0 items-start pl-2'):
                                    ui.label(f'{title} ({count})').classes('text-2xl font-black text-slate-800')

                            with ui.column().classes('w-full gap-2 mt-2'):
                                is_empty = (count <= 0)
                                is_completely_empty = (
                                            total_available is not None and total_available <= 0) if total_available is not None else is_empty

                                if is_empty:
                                    ui.button(btn_text).classes(
                                        'w-full text-sm bg-slate-300 text-slate-500 rounded-xl py-2.5 font-semibold justify-center cursor-not-allowed').props(
                                        'disable')
                                else:
                                    ui.button(btn_text, on_click=lambda r=route: ui.navigate.to(r)).classes(
                                        'w-full text-sm bg-slate-900 text-white rounded-xl py-2.5 font-semibold justify-center cursor-pointer')

                                opts_click = on_options_click if on_options_click else (
                                    lambda r=route: ui.navigate.to(r))

                                if is_completely_empty:
                                    ui.button('options >').props('flat disable').classes(
                                        'w-full text-sm text-slate-400 bg-slate-100 rounded-xl py-2.5 font-medium justify-center cursor-not-allowed')
                                else:
                                    ui.button('options >', on_click=opts_click).props('flat').classes(
                                        'w-full text-sm text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-xl py-2.5 font-medium justify-center cursor-pointer')

                    action_card('Lessons', display_lessons, 'school',
                                route=f'/study/{deck_id}?limit={display_lessons}',
                                on_options_click=lesson_options_dialog.open, total_available=available_lessons)

                    # bound the review options dialog and passed total_reviewable
                    action_card('Reviews', initial_reviews, 'history', btn_text='Start >',
                                route=f'/review/{deck_id}', on_options_click=review_options_dialog.open,
                                total_available=total_reviewable)

                    with ui.column().classes(
                            'p-6 bg-white border border-slate-200 rounded-2xl gap-6 flex-1 shadow-sm hover:shadow-md transition-shadow justify-center items-start text-left'):
                        with ui.column().classes('w-full gap-2 items-start'):
                            dow_labels = ['S', 'M', 'T', 'W', 'T', 'F', 'S']
                            week_status = get_current_week_status(today_ref, deck_id)

                            with ui.element('div').classes(
                                    'grid grid-cols-7 w-full text-center items-center gap-1'):
                                for idx, label in enumerate(dow_labels):
                                    with ui.column().classes('items-center gap-1'):
                                        ui.label(label).classes('text-[11px] font-bold text-slate-400')
                                        d_obj, did_study = week_status[idx]
                                        if did_study:
                                            ui.icon('check', size='xs').classes('text-blue-500 font-bold')
                                        else:
                                            ui.icon('close', size='xs').classes('text-slate-300')

                        curr_streak, best_streak = calculate_streaks(today_ref, deck_id)
                        curr_streak_str = f"{curr_streak} Day" if curr_streak == 1 else f"{curr_streak} Days"
                        best_streak_str = f"{best_streak} Day" if best_streak == 1 else f"{best_streak} Days"

                        with ui.column().classes('w-full gap-1.5 items-start text-left'):
                            ui.label('STUDY STREAK').classes(
                                'text-[11px] font-bold uppercase tracking-wider text-slate-400')
                            ui.label(curr_streak_str).classes('text-3xl font-black text-slate-900')
                            ui.element('div').classes('w-full h-[1px] bg-slate-100 my-3')
                            ui.label('BEST').classes(
                                'text-[11px] font-bold uppercase tracking-wider text-slate-400 mt-1')
                            ui.label(best_streak_str).classes('text-base font-bold text-slate-600')

            render_dashboard_cards()
            app_state['refresh_dashboard_cards'] = render_dashboard_cards.refresh

            activity_state = {'year': date.today().year, 'selected_type': 'year', 'selected_value': None}

            with ui.column().classes('w-full p-8 bg-white border border-slate-200 rounded-2xl gap-4 shadow-sm'):
                @ui.refreshable
                def render_heatmap():
                    current_year = activity_state['year']
                    is_current_year = (current_year >= date.today().year)

                    conn = get_db()
                    cursor = conn.cursor()
                    cursor.execute('SELECT id FROM decks WHERE name = ?', (app_state['current_deck'],))
                    d_row = cursor.fetchone()
                    conn.close()
                    hm_deck_id = d_row['id'] if d_row else 0

                    with ui.row().classes('w-full justify-between items-center'):
                        ui.label('Activity').classes('text-lg font-bold text-slate-800')
                        with ui.row().classes('items-center gap-1.5'):
                            ui.icon('chevron_left', size='sm').classes(
                                'cursor-pointer hover:text-slate-800 text-slate-500').on('click',
                                                                                         lambda: change_year(-1))
                            ui.label(str(current_year)).classes('text-sm font-medium text-slate-700')
                            if is_current_year:
                                ui.icon('chevron_right', size='sm').classes('text-slate-300 cursor-not-allowed')
                            else:
                                ui.icon('chevron_right', size='sm').classes(
                                    'cursor-pointer hover:text-slate-800 text-slate-500').on('click',
                                                                                             lambda: change_year(1))

                    if activity_state['selected_type'] == 'month':
                        m_num = activity_state['selected_value']
                        m_act = get_month_activity(current_year, m_num, hm_deck_id)
                        m_name_str = date(current_year, m_num, 1).strftime('%B')
                        summary_text = f"{m_name_str} {current_year} / Lessons {m_act['lessons']}, Reviews {m_act['reviews']}"
                    elif activity_state['selected_type'] == 'day':
                        d_obj, l_cnt, r_cnt = activity_state['selected_value']
                        date_str = d_obj.strftime('%b %d, %Y')
                        summary_text = f"{date_str} / Lessons {l_cnt}, Reviews {r_cnt}"
                    else:
                        y_act = get_year_activity(current_year, hm_deck_id)
                        summary_text = f"{current_year} / Lessons {y_act['lessons']}, Reviews {y_act['reviews']}"

                    ui.label(summary_text).classes('text-sm text-slate-500 font-medium order-last mt-2')

                    with ui.column().classes('w-full gap-1.5 pt-2 pb-1'):
                        d_start = date(current_year, 1, 1)
                        d_end = date(current_year, 12, 31)

                        def get_sunday_first_index(d):
                            return (d.weekday() + 1) % 7

                        start_padding = get_sunday_first_index(d_start)

                        weeks = []
                        current_week = [None] * 7
                        for i in range(start_padding): current_week[i] = None

                        runner = d_start
                        while runner <= d_end:
                            idx = get_sunday_first_index(runner)
                            current_week[idx] = runner
                            if idx == 6:
                                weeks.append(current_week)
                                current_week = [None] * 7
                            runner += timedelta(days=1)

                        if any(current_week[i] is not None for i in range(7)):
                            weeks.append(current_week)

                        num_weeks = len(weeks)

                        with ui.element('div').classes(
                                f'grid grid-cols-[24px_repeat({num_weeks},minmax(0,1fr))] gap-1 w-full items-center h-5'):
                            ui.element('div')
                            month_start_weeks = {}
                            for w_idx, wk in enumerate(weeks):
                                for day in wk:
                                    if day and day.day == 1:
                                        m_name = day.strftime('%b')
                                        m_num = day.month
                                        if m_num not in [val[1] for val in month_start_weeks.values()]:
                                            month_start_weeks[w_idx] = (m_name, m_num)

                            def make_month_click_handler(m_num):
                                def handle_month_click():
                                    if activity_state['selected_type'] == 'month' and activity_state[
                                        'selected_value'] == m_num:
                                        activity_state['selected_type'] = 'year'
                                        activity_state['selected_value'] = None
                                    else:
                                        activity_state['selected_type'] = 'month'
                                        activity_state['selected_value'] = m_num
                                    render_heatmap.refresh()

                                return handle_month_click

                            for w_idx in range(num_weeks):
                                with ui.element('div').classes('relative'):
                                    if w_idx in month_start_weeks:
                                        m_name, m_num = month_start_weeks[w_idx]
                                        is_selected = (activity_state['selected_type'] == 'month' and activity_state[
                                            'selected_value'] == m_num)
                                        font_cls = 'font-bold text-slate-900 underline' if is_selected else 'font-medium text-slate-500 hover:text-slate-800'
                                        ui.label(m_name).classes(
                                            f'absolute left-0 text-[11px] {font_cls} cursor-pointer whitespace-nowrap').on(
                                            'click', make_month_click_handler(m_num))

                        dow = ['S', 'M', 'T', 'W', 'T', 'F', 'S']

                        def make_click_handler(d_obj, l_cnt, r_cnt):
                            def handle_day_click():
                                if activity_state['selected_type'] == 'day' and activity_state['selected_value'][
                                    0] == d_obj:
                                    activity_state['selected_type'] = 'year'
                                    activity_state['selected_value'] = None
                                else:
                                    activity_state['selected_type'] = 'day'
                                    activity_state['selected_value'] = (d_obj, l_cnt, r_cnt)
                                render_heatmap.refresh()

                            return handle_day_click

                        for r_idx, day_name in enumerate(dow):
                            with ui.element('div').classes(
                                    f'grid grid-cols-[24px_repeat({num_weeks},minmax(0,1fr))] gap-1 w-full items-center'):
                                ui.label(day_name).classes('text-xs text-slate-400 font-medium text-center')

                                for week in weeks:
                                    day_obj = week[r_idx]
                                    if day_obj is None:
                                        ui.element('div').classes('w-full aspect-square bg-transparent')
                                    else:
                                        act_data = get_activity_from_db(day_obj, hm_deck_id)
                                        l_count = act_data['lessons']
                                        r_count = act_data['reviews']
                                        total_activity = l_count + r_count

                                        color = 'bg-slate-100 hover:bg-slate-200'
                                        if total_activity > 0: color = 'bg-[#a3c9e8] hover:bg-[#8eb8e5]'
                                        if total_activity >= 5: color = 'bg-[#73a6d4] hover:bg-[#5898d4]'
                                        if total_activity >= 15: color = 'bg-[#4387c4] hover:bg-[#3472a8]'

                                        is_today = (day_obj == today_ref)
                                        today_border = 'border-2 border-slate-900' if is_today else ''

                                        ui.element('div').classes(
                                            f'w-full aspect-square rounded-sm cursor-pointer transition-colors {color} {today_border}').on(
                                            'click', make_click_handler(day_obj, l_count, r_count))

                def change_year(delta):
                    activity_state['year'] += delta
                    activity_state['selected_type'] = 'year'
                    activity_state['selected_value'] = None
                    render_heatmap.refresh()

                render_heatmap()
                app_state['refresh_heatmap'] = render_heatmap.refresh



