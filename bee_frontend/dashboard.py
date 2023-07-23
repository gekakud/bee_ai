import streamlit as st
import pymongo
from datetime import datetime
import numpy as np

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

def convert_and_get_weight_data():
    # Retrieve all records from the collection
    cursor = collection.find({})

    # Extract timestamp and weight from each record and store in lists
    timestamps = []
    weights = []
    for record in cursor:
        timestamp = datetime.fromisoformat(record['timestamp'])
        weight = record['weight']
        timestamps.append(timestamp)
        weights.append(weight)

    # Convert lists to NumPy arrays
    timestamps_array = np.array(timestamps)
    weights_array = np.array(weights)

    return timestamps_array, weights_array

with st.sidebar:
    all_hives = get_all_device_ids()
    hive_selection = st.selectbox('Choose hive', options=all_hives)
    
if hive_selection:
    hive_data = load_records_by_device_id(hive_selection)
    all_records_list = convert_and_get_weight_data()
    # print(hive_data)
    timestamps, weights = convert_and_get_weight_data()

    st.line_chart(weights, use_container_width=True)
    
