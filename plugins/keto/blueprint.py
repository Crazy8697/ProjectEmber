from datetime import date as _date_cls, datetime, timedelta as _timedelta

from flask import Blueprint, jsonify, render_template, request

from .db import (
    add_event,
    compute_rolling_avg,
    create_recipe,
    delete_event,
    delete_recipe,
    get_chart_daily,
    get_chart_monthly,
    get_chart_per_meal,
    get_chart_weekly,
    get_day_summary,
    get_event_by_id,
    get_recipe_by_id,
    get_targets,
    list_events_for_date,
    list_recipes,
    recipe_to_event_payload,
    update_event,
    update_recipe,
    update_targets,
)

keto_bp = Blueprint(
    "keto",
    __name__,
    url_prefix="/keto",
    template_folder="templates",
    static_folder="static",
)


@keto_bp.get("/")
def keto_index():
    return render_template("keto/index.html")


@keto_bp.get("/recipes")
def recipes_index():
    return render_template("recipes/index.html")


@keto_bp.get("/api/health")
def keto_health():
    return jsonify({"ok": True, "plugin": "keto"})


@keto_bp.get("/api/targets")
def keto_targets():
    row = get_targets()
    if row is None:
        return jsonify({"ok": False, "error": "No targets found"}), 404

    return jsonify(
        {
            "ok": True,
            "targets": {
                "calories_target": row["calories_target"],
                "protein_target_g": row["protein_target_g"],
                "fat_target_g": row["fat_target_g"],
                "net_carbs_target_g": row["net_carbs_target_g"],
                "water_target_ml": row["water_target_ml"],
                "sodium_target_mg": row["sodium_target_mg"],
                "potassium_target_mg": row["potassium_target_mg"],
                "magnesium_target_mg": row["magnesium_target_mg"],
                "expected_events_per_day": row["expected_events_per_day"],
            },
        }
    )


@keto_bp.put("/api/targets")
def keto_update_targets():
    data = request.get_json(silent=True) or {}
    try:
        values = {
            "calories_target":       float(data.get("calories_target", 0) or 0),
            "protein_target_g":      float(data.get("protein_target_g", 0) or 0),
            "fat_target_g":          float(data.get("fat_target_g", 0) or 0),
            "net_carbs_target_g":    float(data.get("net_carbs_target_g", 0) or 0),
            "water_target_ml":       float(data.get("water_target_ml", 0) or 0),
            "sodium_target_mg":      float(data.get("sodium_target_mg", 0) or 0),
            "potassium_target_mg":   float(data.get("potassium_target_mg", 0) or 0),
            "magnesium_target_mg":   float(data.get("magnesium_target_mg", 0) or 0),
            "expected_events_per_day": float(data.get("expected_events_per_day", 4) or 4),
        }
        negative = [k for k, v in values.items() if v < 0]
        if negative:
            return jsonify({"ok": False, "error": f"Target values must be non-negative: {', '.join(negative)}"}), 400
        update_targets(**values)
    except (ValueError, TypeError) as e:
        return jsonify({"ok": False, "error": str(e)}), 400
    return jsonify({"ok": True})


@keto_bp.get("/api/events")
def keto_list_events():
    event_date = request.args.get("date", "").strip()
    if not event_date:
        return jsonify({"ok": False, "error": "Missing required query parameter: date"}), 400

    rows = list_events_for_date(event_date)
    return jsonify({"ok": True, "events": rows, "date": event_date})


@keto_bp.get("/api/events/<int:event_id>")
def keto_get_event(event_id: int):
    row = get_event_by_id(event_id)
    if row is None:
        return jsonify({"ok": False, "error": "Event not found"}), 404

    return jsonify({"ok": True, "event": row})


@keto_bp.post("/api/events")
def keto_add_event():
    data = request.get_json(silent=True) or {}

    required_fields = ["event_timestamp", "event_date", "event_type", "label"]
    missing = [field for field in required_fields if not str(data.get(field, "")).strip()]
    if missing:
        return jsonify({"ok": False, "error": f"Missing required fields: {', '.join(missing)}"}), 400

    try:
        event_id = add_event(
            event_timestamp=str(data["event_timestamp"]).strip(),
            event_date=str(data["event_date"]).strip(),
            event_type=str(data["event_type"]).strip(),
            label=str(data["label"]).strip(),
            calories=float(data.get("calories", 0) or 0),
            protein_g=float(data.get("protein_g", 0) or 0),
            fat_g=float(data.get("fat_g", 0) or 0),
            net_carbs_g=float(data.get("net_carbs_g", 0) or 0),
            water_ml=float(data.get("water_ml", 0) or 0),
            sodium_mg=float(data.get("sodium_mg", 0) or 0),
            potassium_mg=float(data.get("potassium_mg", 0) or 0),
            magnesium_mg=float(data.get("magnesium_mg", 0) or 0),
            source=str(data.get("source", "manual")).strip() or "manual",
            source_id=(str(data["source_id"]).strip() if data.get("source_id") is not None else None),
            notes=(str(data["notes"]).strip() if data.get("notes") is not None else None),
        )
    except ValueError as e:
        return jsonify({"ok": False, "error": str(e)}), 400

    return jsonify({"ok": True, "event_id": event_id}), 201


@keto_bp.put("/api/events/<int:event_id>")
def keto_update_event(event_id: int):
    data = request.get_json(silent=True) or {}

    required_fields = ["event_timestamp", "event_date", "event_type", "label"]
    missing = [field for field in required_fields if not str(data.get(field, "")).strip()]
    if missing:
        return jsonify({"ok": False, "error": f"Missing required fields: {', '.join(missing)}"}), 400

    try:
        updated = update_event(
            event_id=event_id,
            event_timestamp=str(data["event_timestamp"]).strip(),
            event_date=str(data["event_date"]).strip(),
            event_type=str(data["event_type"]).strip(),
            label=str(data["label"]).strip(),
            calories=float(data.get("calories", 0) or 0),
            protein_g=float(data.get("protein_g", 0) or 0),
            fat_g=float(data.get("fat_g", 0) or 0),
            net_carbs_g=float(data.get("net_carbs_g", 0) or 0),
            water_ml=float(data.get("water_ml", 0) or 0),
            sodium_mg=float(data.get("sodium_mg", 0) or 0),
            potassium_mg=float(data.get("potassium_mg", 0) or 0),
            magnesium_mg=float(data.get("magnesium_mg", 0) or 0),
            source=str(data.get("source", "manual")).strip() or "manual",
            source_id=(str(data["source_id"]).strip() if data.get("source_id") is not None else None),
            notes=(str(data["notes"]).strip() if data.get("notes") is not None else None),
        )
    except ValueError as e:
        return jsonify({"ok": False, "error": str(e)}), 400

    if not updated:
        return jsonify({"ok": False, "error": "Event not found"}), 404

    return jsonify({"ok": True, "event_id": event_id})


@keto_bp.delete("/api/events/<int:event_id>")
def keto_delete_event(event_id: int):
    deleted = delete_event(event_id)
    if not deleted:
        return jsonify({"ok": False, "error": "Event not found"}), 404

    return jsonify({"ok": True, "event_id": event_id})


@keto_bp.get("/api/day")
def keto_day_summary():
    event_date = request.args.get("date", "").strip()
    if not event_date:
        return jsonify({"ok": False, "error": "Missing required query parameter: date"}), 400

    return jsonify({"ok": True, **get_day_summary(event_date)})


@keto_bp.get("/api/graphs")
def keto_graphs():
    date_from = request.args.get("from", "").strip()
    date_to   = request.args.get("to",   "").strip()
    mode      = request.args.get("mode", "daily").strip()
    avg       = request.args.get("avg",  "none").strip()

    if not date_from or not date_to:
        return jsonify({"ok": False, "error": "Missing required parameters: from, to"}), 400

    if mode not in ("daily", "per_meal", "weekly", "monthly"):
        return jsonify({"ok": False, "error": "mode must be 'daily', 'weekly', 'monthly', or 'per_meal'"}), 400

    if avg not in ("none", "7", "14", "30"):
        return jsonify({"ok": False, "error": "avg must be 'none', '7', '14', or '30'"}), 400

    try:
        if mode == "per_meal":
            rows    = get_chart_per_meal(date_from, date_to)
            rolling = []
        elif mode == "weekly":
            rows    = get_chart_weekly(date_from, date_to)
            rolling = []
        elif mode == "monthly":
            rows    = get_chart_monthly(date_from, date_to)
            rolling = []
        else:
            if avg != "none":
                window        = int(avg)
                extended_from = (
                    _date_cls.fromisoformat(date_from) - _timedelta(days=window - 1)
                ).isoformat()
                all_daily = get_chart_daily(extended_from, date_to)
            else:
                all_daily = get_chart_daily(date_from, date_to)

            rows    = [r for r in all_daily if r["date"] >= date_from]
            rolling = (
                compute_rolling_avg(all_daily, int(avg), date_from, date_to)
                if avg != "none"
                else []
            )
    except ValueError as exc:
        return jsonify({"ok": False, "error": str(exc)}), 400

    return jsonify({
        "ok":      True,
        "mode":    mode,
        "avg":     avg,
        "from":    date_from,
        "to":      date_to,
        "rows":    rows,
        "rolling": rolling,
    })


# -----------------------------
# Recipes API
# -----------------------------

@keto_bp.get("/api/recipes")
def keto_list_recipes():
    return jsonify({"ok": True, "recipes": list_recipes()})


@keto_bp.get("/api/recipes/<int:recipe_id>")
def keto_get_recipe(recipe_id: int):
    recipe = get_recipe_by_id(recipe_id)
    if recipe is None:
        return jsonify({"ok": False, "error": "Recipe not found"}), 404
    return jsonify({"ok": True, "recipe": recipe})


@keto_bp.post("/api/recipes")
def keto_create_recipe():
    data = request.get_json(silent=True) or {}
    name = str(data.get("name", "")).strip()
    if not name:
        return jsonify({"ok": False, "error": "Recipe name is required"}), 400

    recipe_id = create_recipe(
        name=name,
        description=str(data.get("description", "") or "").strip(),
        keto_notes=str(data.get("keto_notes", "") or "").strip(),
        servings=float(data.get("servings", 1) or 1),
        calories=float(data.get("calories", 0) or 0),
        protein_g=float(data.get("protein_g", 0) or 0),
        fat_g=float(data.get("fat_g", 0) or 0),
        net_carbs_g=float(data.get("net_carbs_g", 0) or 0),
        water_ml=float(data.get("water_ml", 0) or 0),
        sodium_mg=float(data.get("sodium_mg", 0) or 0),
        potassium_mg=float(data.get("potassium_mg", 0) or 0),
        magnesium_mg=float(data.get("magnesium_mg", 0) or 0),
        ingredients=data.get("ingredients", []) or [],
    )
    return jsonify({"ok": True, "recipe_id": recipe_id}), 201


@keto_bp.put("/api/recipes/<int:recipe_id>")
def keto_update_recipe(recipe_id: int):
    data = request.get_json(silent=True) or {}
    name = str(data.get("name", "")).strip()
    if not name:
        return jsonify({"ok": False, "error": "Recipe name is required"}), 400

    updated = update_recipe(
        recipe_id=recipe_id,
        name=name,
        description=str(data.get("description", "") or "").strip(),
        keto_notes=str(data.get("keto_notes", "") or "").strip(),
        servings=float(data.get("servings", 1) or 1),
        calories=float(data.get("calories", 0) or 0),
        protein_g=float(data.get("protein_g", 0) or 0),
        fat_g=float(data.get("fat_g", 0) or 0),
        net_carbs_g=float(data.get("net_carbs_g", 0) or 0),
        water_ml=float(data.get("water_ml", 0) or 0),
        sodium_mg=float(data.get("sodium_mg", 0) or 0),
        potassium_mg=float(data.get("potassium_mg", 0) or 0),
        magnesium_mg=float(data.get("magnesium_mg", 0) or 0),
        ingredients=data.get("ingredients", []) or [],
    )
    if not updated:
        return jsonify({"ok": False, "error": "Recipe not found"}), 404
    return jsonify({"ok": True, "recipe_id": recipe_id})


@keto_bp.delete("/api/recipes/<int:recipe_id>")
def keto_delete_recipe(recipe_id: int):
    deleted = delete_recipe(recipe_id)
    if not deleted:
        return jsonify({"ok": False, "error": "Recipe not found"}), 404
    return jsonify({"ok": True, "recipe_id": recipe_id})


@keto_bp.post("/api/recipes/<int:recipe_id>/log")
def keto_log_recipe(recipe_id: int):
    data = request.get_json(silent=True) or {}
    recipe = get_recipe_by_id(recipe_id)
    if recipe is None:
        return jsonify({"ok": False, "error": "Recipe not found"}), 404

    event_date = str(data.get("event_date", "")).strip()
    event_time = str(data.get("event_time", "")).strip()

    if not event_date:
        event_date = datetime.now().strftime("%Y-%m-%d")
    if not event_time:
        event_time = datetime.now().strftime("%H:%M")

    event_timestamp = f"{event_date}T{event_time}:00"
    payload = recipe_to_event_payload(recipe, event_date=event_date, event_timestamp=event_timestamp)

    event_id = add_event(**payload)
    return jsonify({"ok": True, "event_id": event_id, "recipe_id": recipe_id})
