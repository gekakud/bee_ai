from google.cloud import storage
import os

def initialize_storage_client(key_file_path):
    # Initialize Google Cloud Storage client with the provided service account key
    client = storage.Client.from_service_account_json(key_file_path)
    return client

key_file_path = "secret-bucksaw-393408-b5d0ee9d9e83.json"
storage_client = initialize_storage_client(key_file_path)

def create_folder(bucket_name, folder_path):
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(folder_path + '/')  # Add a trailing slash to indicate it's a folder
    blob.upload_from_string('')  # Uploading an empty string creates a folder

def upload_data(device_id, data_type, file_path, date):
    # Define the bucket and folder paths
    bucket_name = "bee_bucket_dev"
    base_folder = f"{device_id}/{data_type}/{date}/"

    # Create the necessary folders (if they don't exist)
    create_folder(bucket_name, f"{device_id}/{data_type}/")
    create_folder(bucket_name, f"{device_id}/{data_type}/{date}/")

    # Upload the file to the corresponding folder
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(base_folder + os.path.basename(file_path))
    blob.upload_from_filename(file_path)

# Example usage
device_id = "dev1"
date = "2023-07-29"  # Replace with the actual date
picture_file_path = "image.png"  # Replace with the actual picture file path

upload_data(device_id, "pics", picture_file_path, date)
