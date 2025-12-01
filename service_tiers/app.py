import asyncio
import os
from flask import Flask, request, jsonify
import time
import random
from notificationapi_python_server_sdk import notificationapi

app = Flask(__name__)

# Initialize NotificationAPI
notificationapi.init(
    os.getenv("NOTIFICATIONAPI_CLIENT_ID"),
    os.getenv("NOTIFICATIONAPI_CLIENT_SECRET")
)

# Configuration
CYCLE_DURATION = 10  # seconds
MOTES = {
    "light1": ["9.138", "32.131", "53.105"],
    "light2": ["9.138", "32.131", "53.105"]
}

def get_current_value(light_id):
    """
    Calculate the current value based on the cycle position.
    Returns high value (200 +/- 10) or low value (50 +/- 10).
    """
    current_time = time.time()
    # Determine position in cycle (0 to CYCLE_DURATION)
    cycle_position = current_time % CYCLE_DURATION
    
    # First half of cycle is "up" (high values), second half is "down" (low values)
    if light_id == "light1" and cycle_position < CYCLE_DURATION / 2:
        # Up part: 200 +/- 10
        base_value = 200
        noise = random.uniform(-10, 10)
    elif light_id == "light2" and cycle_position >= CYCLE_DURATION / 2:
        # Up part: 200 +/- 10
        base_value = 200
        noise = random.uniform(-10, 10)

    else:
        # Down part: 50 +/- 10
        base_value = 50
        noise = random.uniform(-10, 10)
    
    return round(base_value + noise, 2)

@app.route('/iotlab/rest/data/1/<light_id>/last', methods=['GET'])
def get_light_data(light_id):
    """
    Returns mock light sensor data for the specified light_id.
    """
    # Validate light_id
    if light_id not in MOTES:
        return jsonify([])
    
    # Get current timestamp in milliseconds
    current_timestamp = int(time.time() * 1000)
    
    # Generate data for each mote
    data = []
    for mote in MOTES[light_id]:
        value = get_current_value(light_id)
        data.append({
            "timestamp": current_timestamp + random.randint(-100, 100),  # Small variation
            "label": light_id,
            "value": value,
            "mote": mote
        })
    
    return jsonify({"data": data})

@app.route('/', methods=['GET'])
def index():
    """
    Simple index route for health checks.
    """
    return jsonify({
        "status": "running",
        "endpoints": [
            "/iotlab/rest/data/1/light1/last",
            "/iotlab/rest/data/1/light2/last",
            "/notify",
            "/health"
        ]
    })


# ===== MAILING SYSTEM ROUTES =====

def build_email_subject(motes_on, motes_off):
    """
    Build email subject based on motes that turned on/off
    """
    total_changes = len(motes_on) + len(motes_off)

    if total_changes == 1:
        if motes_on:
            return f"AMIO - Lumière allumée: {motes_on[0]}"
        else:
            return f"AMIO - Lumière éteinte: {motes_off[0]}"
    else:
        return f"AMIO - {total_changes} changements détectés"


def build_email_html_content(motes_on, motes_off):
    """
    Build HTML content for the email body
    """
    html = ["<html><body>"]
    html.append("<h2>AMIO - Alerte Capteurs</h2>")

    if motes_on:
        html.append("<h3 style='color: #ff9800;'>💡 LUMIÈRES ALLUMÉES</h3>")
        html.append("<ul>")
        for mote in motes_on:
            html.append(f"<li>{mote}</li>")
        html.append("</ul>")

    if motes_off:
        html.append("<h3 style='color: #4caf50;'>🌙 LUMIÈRES ÉTEINTES</h3>")
        html.append("<ul>")
        for mote in motes_off:
            html.append(f"<li>{mote}</li>")
        html.append("</ul>")

    html.append("<p><small>Cet email a été envoyé automatiquement par l'application AMIO.</small></p>")
    html.append("</body></html>")

    return "".join(html)


async def send_email_async(recipient_email, subject, html_content):
    """
    Send email using NotificationAPI
    """
    await notificationapi.send({
        "type": os.getenv("NOTIFICATION_TYPE", "mote_update"),
        "to": {
            "id": recipient_email,
            "email": recipient_email
        },
        "email": {
            "subject": subject,
            "html": html_content,
            "senderName": os.getenv("SENDER_NAME", "AMIO System"),
            "senderEmail": os.getenv("SENDER_EMAIL", "noreply@amio.com")
        }
    })


@app.route('/notify', methods=['POST'])
def notify():
    """
    Endpoint to receive motes status changes and send email notification
    Accepts form-encoded data with comma-separated mote lists
    """
    try:
        # Get form data (application/x-www-form-urlencoded)
        recipient_email = request.form.get('recipientEmail', '').strip()
        motes_on_str = request.form.get('motesOn', '').strip()
        motes_off_str = request.form.get('motesOff', '').strip()
        
        # Validate recipient email
        if not recipient_email:
            return jsonify({"error": "recipientEmail is required"}), 400
        
        # Parse comma-separated strings into lists
        motes_on = [m.strip() for m in motes_on_str.split(',') if m.strip()] if motes_on_str else []
        motes_off = [m.strip() for m in motes_off_str.split(',') if m.strip()] if motes_off_str else []
        
        # Check if we have any changes
        if not motes_on and not motes_off:
            return jsonify({"error": "No changes detected"}), 400
        
        # Build email content
        subject = build_email_subject(motes_on, motes_off)
        html_content = build_email_html_content(motes_on, motes_off)
        
        # Send email
        asyncio.run(send_email_async(recipient_email, subject, html_content))
        
        return jsonify({
            "success": True,
            "message": "Email sent successfully",
            "recipientEmail": recipient_email,
            "changes": {
                "motesOn": motes_on,
                "motesOff": motes_off
            }
        }), 200
        
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/health', methods=['GET'])
def health():
    """
    Health check endpoint
    """
    return jsonify({"status": "healthy"}), 200

if __name__ == '__main__':
    # Validate required environment variables for mailing system
    required_vars = ["NOTIFICATIONAPI_CLIENT_ID", "NOTIFICATIONAPI_CLIENT_SECRET"]
    missing_vars = [var for var in required_vars if not os.getenv(var)]
    
    if missing_vars:
        print(f"WARNING: Missing environment variables for mailing system: {', '.join(missing_vars)}")
        print("Mailing functionality will not work without these variables.")
    
    port = int(os.getenv('PORT', 8000))
    app.run(host='0.0.0.0', port=port, debug=os.getenv('DEBUG', 'False').lower() == 'true')
