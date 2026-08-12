ACTIVE_STYLE = 'bg-blue-50 text-blue-600 border-blue-200 font-medium'
INACTIVE_STYLE = 'text-slate-600 hover:bg-slate-200 border-transparent font-normal'

app_state = {
    'current_deck': 'Try creating a deck!',
    'lessons_label': None,
    'reviews_label': None,
    'title_label': None
}
deck_elements_pool = []
fn_sidebar_render_container = None