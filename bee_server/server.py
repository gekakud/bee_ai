from flask import Flask, render_template, request, jsonify
import pymongo
from bson import json_util
import datetime
from g_storage import upload_data


#  ALL CONFIGS
host_url = '127.0.0.1'

DEBUG_MODE = False

if DEBUG_MODE:
    uri = 'mongodb://localhost:27017/'
else:
    host_url = '0.0.0.0'
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

@app.route('/api/upload', methods=['POST'])
def upload_file():
    if 'image' not in request.files or 'video' not in request.files:
        return jsonify({'error': 'No media part provided in request'})

    data_type = ''
    
    if 'image' in request.files:
        media_file = request.files['image']
        data_type = 'pics'
    else:
        media_file = request.files['video']
        data_type = 'video'

    if not media_file:
        return jsonify({'error': 'cannot extract media payload'})

    if media_file.filename == '':
        return jsonify({'error': 'No selected image'})

    if media_file:
        try:
            current_datetime = datetime.datetime.now()
            date = current_datetime.strftime("%Y-%m-%d")
            datetime_full = current_datetime.strftime("%Y-%m-%d %H:%M:%S")

            filename = datetime_full + '_' + media_file.filename

            media_file.save(filename)

            device_id = "dev1"
        except Exception as exc:
            print(str(exc))
            return jsonify({'error': 'save file failed'})

        try:
            upload_data(device_id, data_type=data_type, file_path=filename, date_folder=date)
        except Exception as exc:
            return jsonify({'error': 'upload to bucket failed'})

        return jsonify({'message': 'media_file uploaded successfully'})

if __name__ == '__main__':
    port = 5000
    app.run(host=host_url, port=5000)
