import streamlit as st
import pymongo
from datetime import datetime
import numpy as np
from streamlit_echarts import st_echarts

mongo_uri = "mongodb+srv://bee_admin:bee_admin_pass@atlascluster.d85negs.mongodb.net/?retryWrites=true&w=majority"

# Connect to MongoDB
client = pymongo.MongoClient(mongo_uri)
db = client['bee_db_dev']  # Replace 'mydatabase' with your database name
collection = db['bee_metadata_col']  # Replace 'mycollection' with your collection name

def load_records_by_device_id(device_id):
    # Define the query filter to find records with the given device_id
    query = {"device_id": device_id}

    # Use the find method with the query filter to retrieve the matching records
    cursor = collection.find(query)

    # Convert the cursor to a list of dictionaries (JSON-like objects)
    records = list(cursor)

    return records

def get_all_device_ids():
    # Use the distinct method to get all unique device_id values
    device_ids = collection.distinct("device_id")

    return device_ids

def convert_and_get_data():
    cursor = collection.find({})
    timestamps, weights, temperatures, humidities, batterys = [], [], [], [], []
    for record in cursor:
        timestamp = record['timestamp']
        weight = record['weight']
        temperature = record['temperature']  # Assume you have this field
        humidity = record['humidity']  # Assume you have this field
        battery = record['battery_level']  # Assume you have this field
        timestamps.append(timestamp)
        weights.append(weight)
        temperatures.append(temperature)
        humidities.append(humidity)
        batterys.append(battery)
    
    # round the results so it will be displayed and viewed easily
    Rweights = [round(w, 2) for w in weights]
    Rtemperatures = [round(t, 2) for t in temperatures]
    Rhumidities = [round(h, 2) for h in humidities]

    return Rweights, timestamps, Rtemperatures, Rhumidities, batterys

with st.sidebar:
    st.header('Yaniv\'s Bee Hive')
    st.image('./resources/log.jpg')
    all_hives = get_all_device_ids()
    hive_selection = st.selectbox('Choose hive', options=all_hives)
    
if hive_selection:
    hive_data = load_records_by_device_id(hive_selection)
    all_records_list = convert_and_get_data()
    # print(hive_data)
    weights, timestamps, temperatures, humidities, batterys = convert_and_get_data()

    option = {
        "title": {"text": "Hive Data Distribution"},
        "tooltip": {"trigger": "axis"},
        "xAxis": {
            "type": "category",
            "data": timestamps,
        },
        "yAxis": [{
            "type": "value",
            "name": "Weight",
            "position": "left"
        }, {
            "type": "value",
            "name": "Temp",
            "position": "right",
            "offset": -5  # adjust as needed to place it in the center
        }, {
            "type": "value",
            "name": "Humidity",
            "position": "right",
            "offset": 40  # adjust as needed to ensure no overlap
        }],

        "series": [
            {"data": weights, "type": "line", "name": "Weight", "color": "#FF5733"},  # Example color
            {"data": temperatures, "type": "line", "name": "Temperature", "yAxisIndex": 1, "color": "#33FF57"},  # Example color
            {"data": humidities, "type": "line", "name": "Humidity", "yAxisIndex": 2, "color": "#3357FF"}  # Example color
        ],

        "legend": {
            "data": ["Weight", "Temperature", "Humidity"],
            "textStyle": {
                "color": "#FFFFFF"  # White color
            }
        },


        "dataZoom": [{
            "type": 'slider',
            "start": 0,
            "end": 100
        }],
    }
    last_battery_level = batterys[-1] if batterys else None  # Retrieve the last battery level if the list is not empty

    st.sidebar.text(f"**Battery Level for {hive_selection}:** {last_battery_level}%")


    #st.line_chart(weights, use_container_width=True)
    st_echarts(options=option, height="400px")
