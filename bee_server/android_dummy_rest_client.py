import requests
import json
import time
import random
from datetime import datetime, timedelta


def generate_demo_data(index):
    device_number = random.randint(1, 9)
    device_id = f"device-{device_number:03d}"
    temperature = round(random.uniform(20, 32.3), 1)
    humidity = round(random.uniform(35, 65), 1)
    weight = round(random.uniform(4.8, 5.8), 2)
    # Calculate timestamp with a 15-minute interval
    base_timestamp = datetime(2023, 7, 20, 12, 0)  # Set the starting timestamp
    interval = timedelta(minutes=15)
    timestamp = (base_timestamp + index * interval).isoformat()
    data = {
        "device_id": device_id,
        "timestamp": timestamp,
        "temperature": temperature,
        "humidity": humidity,
        "weight": weight,
        "light": {"lux": 500, "is_dark": False},
        "pressure": {"value": 1012.5, "unit": "hPa"},
        "battery_level": 78,
        "status": "online",
        "pic":{"pic_id":"1234","sync":True},
        "vid":{"vid_id":"4321","sync":False}
    }
    return data

def send_data_request(data):
    url = 'http://127.0.0.1:5000/api/data'
    headers = {'Content-Type': 'application/json'}
    response = requests.post(url, data=json.dumps(data), headers=headers)
    print(f"Response for request {data['timestamp']}: {response.text}")

def main():
    interval = 0.02  # 20ms interval (0.02 seconds)
    for i in range(1000):
        demo_data = generate_demo_data(i)
        send_data_request(demo_data)
        time.sleep(interval)

if __name__ == "__main__":
    main()