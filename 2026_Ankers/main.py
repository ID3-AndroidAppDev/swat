from nicegui import app, ui
from database import init_db
from study import study_page

@ui.page('/study/{deck_id}')
def study_route(deck_id: int, limit: int = None):
    study_page(deck_id, limit=limit)

import home
import review
import study

init_db()

app.add_static_files('/static', 'static')

ui.run(port=8080, title='Ankers Flashcards', storage_secret='ankers_secret_key_123')