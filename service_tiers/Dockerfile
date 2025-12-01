FROM python:3.11-slim

WORKDIR /app

# Copy requirements and install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code and environment file
COPY app.py .
COPY .env .

# Expose port 8000
EXPOSE 8000

# Run the application
CMD ["python", "app.py"]
