import streamlit as st
import pymongo
from datetime import datetime
import numpy as np
import pandas as pd
from streamlit_echarts import st_echarts
from g_bucket_service import get_image_urls


mongo_uri = "mongodb+srv://bee_admin:bee_admin_pass@atlascluster.d85negs.mongodb.net/?retryWrites=true&w=majority"

# Connect to MongoDB
client = pymongo.MongoClient(mongo_uri)
db = client['bee_db_dev'] 
collection = db['bee_metadata_col']  

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
    timestamps, weights, temperatures, humidities, batterys, locations = [], [], [], [], [], []
    for record in cursor:
        timestamp = record['timestamp']
        weight = record['weight']
        temperature = record.get('temperature', None)  # Using the get method with a default value
        humidity = record.get('humidity', None)  # Using the get method with a default value
        battery = record['battery_level'] 
        
        if temperature == -200:
            temperature = None  # Replace invalid temperature with None

        if humidity == -200:
            humidity = None  # Replace invalid humidity with None

        if 'location' in record:
            location = record['location']
            locations.append(location)

        else:
            location = None 
        
        timestamps.append(timestamp)
        weights.append(weight)
        temperatures.append(temperature)
        humidities.append(humidity)
        batterys.append(battery)
    
    # round the results so it will be displayed and viewed easily
    Rweights = [round(w, 2) for w in weights]
    Rtemperatures = [round(t, 2) if t is not None else None for t in temperatures]  # Handling None
    Rhumidities = [round(t, 2) if t is not None else None for t in humidities]  # Handling None

    return Rweights, timestamps, Rtemperatures, Rhumidities, batterys, locations

with st.sidebar:
    st.header('Yaniv\'s Bee Hive')
    st.image('./resources/log.jpg')
    all_hives = get_all_device_ids()
    hive_selection = st.selectbox('Choose hive', options=all_hives)
    
if hive_selection:
    hive_data = load_records_by_device_id(hive_selection)
    all_records_list = convert_and_get_data()
    # print(hive_data)
    weights, timestamps, temperatures, humidities, batterys, locations = convert_and_get_data()
    
    MaxWeight = max(weights)
    index_of_max_weight = weights.index(MaxWeight)
    timestamp_of_max_weight = timestamps[index_of_max_weight]
    
    MinWeight = min(weights)
    index_of_min_weight = weights.index(MinWeight)
    timestamp_of_min_weight = timestamps[index_of_min_weight]
    
    MaxTemp = max(temp for temp in temperatures if temp is not None)
    index_of_max_temp = temperatures.index(MaxTemp)
    timestamp_of_max_temp = timestamps[index_of_max_temp]
    
    MinTemp = min(temp for temp in temperatures if temp is not None)
    index_of_min_temp = temperatures.index(MinTemp)
    timestamp_of_min_temp = timestamps[index_of_min_temp]
    

    content = f"""
    <div style="line-height:1.5;">
    The maximum weight is {MaxWeight} Kg, Occurred at: {timestamp_of_max_weight}<br>
    The minimum weight is {MinWeight} Kg, Occurred at: {timestamp_of_min_weight}<br>
    The maximum temp is {MaxTemp} °C, Occurred at: {timestamp_of_max_temp}<br>
    The minimum temp is {MinTemp} °C, Occurred at: {timestamp_of_min_temp}
    </div>
    """

    st.markdown(content, unsafe_allow_html=True)


    # Assuming timestamps is a list of string timestamps
    formatted_timestamps = [datetime.strptime(value, '%Y-%m-%dT%H:%M:%S').strftime('%d-%m-%Y\n%H:%M:%S') for value in timestamps]

    # Option for the weight chart
    option_weight = {
        "title": {"text": "Hive Weight Distribution"},
        "tooltip": {"trigger": "axis"},
        "xAxis": {
            "type": "category",
            "data": formatted_timestamps,
            "axisLabel": {
                "rotate": 0,
            }
        },
        "yAxis": {
            "type": "value",
            "name": "Weight",
        },
        "series": [{"data": weights, "type": "line", "name": "Weight", "color": "#FF5733"}],
        "dataZoom": [{
            "type": 'slider',
            "start": 0,
            "end": 100
        }],
    }

    # Option for the temperature and humidity chart
    option_temp_humidity = {
        "title": {"text": "Hive Temperature and Humidity Distribution"},
        "tooltip": {"trigger": "axis"},
        "xAxis": {
            "type": "category",
            "data": formatted_timestamps,
            "axisLabel": {
                "rotate": 0,
            }
        },
        "yAxis": [
            {
                "type": "value",
                "name": "Temp",
                "position": "left",
            },
            {
                "type": "value",
                "name": "Humidity",
                "position": "right",
            }
        ],
        "series": [
            {"data": temperatures, "type": "line", "name": "Temperature", "yAxisIndex": 0, "color": "#33FF57"},
            {"data": humidities, "type": "line", "name": "Humidity", "yAxisIndex": 1, "color": "#3357FF"}
        ],
        "dataZoom": [{
            "type": 'slider',
            "start": 0,
            "end": 100
        }],
    }

    # Render the weight chart
    st_echarts(options=option_weight, height="400px")

    # Render the temperature and humidity chart
    st_echarts(options=option_temp_humidity, height="400px")
            
    image_links = get_image_urls()

    for link in image_links:
        st.markdown(f"[{link['name']}]({link['url']})")
        
    last_battery_level = batterys[-1] if batterys else None  # Retrieve the last battery level if the list is not empty
  
    #st.line_chart(weights, use_container_width=True)
  #  st_echarts(options=option, height="400px")

    st.sidebar.metric(label="Battery level", value=f"{last_battery_level}%")
 
    last_location = locations[-1] if locations else None
    if last_location:
        df = pd.DataFrame({
            'lat': [float(last_location["latitude"])],
            'lon': [float(last_location["longitude"])]
        })
    else:
        df = pd.DataFrame({
            'lat': [0.0],
            'lon': [0.0]
        })        
    
        
    
    # Display the map
    st.map(df)
    