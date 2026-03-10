from .blueprint import keto_bp
from .db import init_db


def init_keto(app):
    init_db()
    app.register_blueprint(keto_bp, url_prefix="/keto")
