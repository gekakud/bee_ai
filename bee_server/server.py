from flask import Flask, render_template, request, jsonify
import pymongo
from bson import json_util

#  ALL CONFIGS
host_url = '127.0.0.1'

DEBUG_MODE = False

if DEBUG_MODE:
    uri = 'mongodb://localhost:27017/'
else:
    uri = "mongodb+srv://bee_admin:bee_admin_pass@atlascluster.d85negs.mongodb.net/?retryWrites=true&w=majority"


app = Flask(__name__)

# Connect to the MongoDB server
client = pymongo.MongoClient(uri)
db = client['bee_db_dev']  # Replace 'mydatabase' with your desired database name
collection = db['bee_metadata_col']  # Replace 'mycollection' with your desired collection name

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/data', methods=['POST'])
def handle_data():
    data = request.get_json()
    # print(data)  # Print the data received from the POST request

    # Insert the data into MongoDB
    collection.insert_one(data)

    return 'Data received'

@app.route('/api/find_by_device_id', methods=['POST'])
def find_by_device_id():
    device_id_to_find = request.get_json().get('device_id')  # Assuming the request contains {'device_id': 'device-001'} for example
    if device_id_to_find is None:
        return 'device_id not provided in the request', 400

    # Find all records where the 'device_id' field matches the given device_id_to_find
    result = list(collection.find({"device_id": device_id_to_find}))
    response_data = json_util.dumps(result)

    return jsonify(response_data)

if __name__ == '__main__':
    app.run(host=host_url)
