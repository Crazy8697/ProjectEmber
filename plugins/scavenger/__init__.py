from .blueprint import scavenger_bp


def init_scavenger(app):
    """Register the scavenger blueprint on *app* and initialise the SQLite DB."""
    from .db import init_db

    init_db()
    app.register_blueprint(scavenger_bp, url_prefix="/scavenger")
