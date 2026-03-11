from flask import Blueprint, jsonify, render_template, request

from .db import (
    add_event,
    delete_event,
    get_day_summary,
    get_event_by_id,
    get_targets,
    list_events_for_date,
    update_event,
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
