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
    timestamps, weights, temperatures, humidities = [], [], [], []
    for record in cursor:
        timestamp = record['timestamp']
        weight = record['weight']
        temperature = record['temperature']  # Assume you have this field
        humidity = record['humidity']  # Assume you have this field
        timestamps.append(timestamp)
        weights.append(weight)
        temperatures.append(temperature)
        humidities.append(humidity)
    
    Rweights = [round(w, 2) for w in weights]
    Rtemperatures = [round(t, 2) for t in temperatures]
    Rhumidities = [round(h, 2) for h in humidities]

    return Rweights, timestamps, Rtemperatures, Rhumidities

with st.sidebar:
    st.header('Yaniv\'s Bee Hive')
    st.image('./resources/log.jpg')
    all_hives = get_all_device_ids()
    hive_selection = st.selectbox('Choose hive', options=all_hives)
    
if hive_selection:
    hive_data = load_records_by_device_id(hive_selection)
    all_records_list = convert_and_get_data()
    # print(hive_data)
    weights, timestamps, temperatures, humidities = convert_and_get_data()

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
            "name": "Temperature",
            "position": "right",
        }, {
            "type": "value",
            "name": "Humidity",
            "position": "right",
            "offset": 60  # You might need to adjust this based on your data and display
        }],
        "series": [
            {"data": weights, "type": "line", "name": "Weight"},
            {"data": temperatures, "type": "line", "name": "Temperature", "yAxisIndex": 1},
            {"data": humidities, "type": "line", "name": "Humidity", "yAxisIndex": 2}
        ],
        "dataZoom": [{
            "type": 'slider',
            "start": 0,
            "end": 100
        }],
    }


    #st.line_chart(weights, use_container_width=True)
    st_echarts(options=option, height="400px")
