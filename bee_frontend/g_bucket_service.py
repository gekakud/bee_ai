from google.cloud import storage
import os, requests

# Define the bucket and folder paths
bucket_name = "bee_bucket_dev"
# base_folder = f"{device_id}/{data_type}/{date_folder}/"

def initialize_storage_client():
    key_file_path = "secret-bucksaw-393408-b5d0ee9d9e83.json"
    # Initialize Google Cloud Storage client with the provided service account key
    client = storage.Client.from_service_account_json(key_file_path)
    return client

def download_image(url, local_path):
    response = requests.get(url)
    if response.status_code == 200:
        with open(local_path, 'wb') as file:
            file.write(response.content)

def get_image_urls():
    client = initialize_storage_client()
    bucket = client.get_bucket(bucket_name)
    
    image_urls = []
    blobs = bucket.list_blobs()

    for blob in blobs:
        # Check if the object is an image (you can customize this check based on your image file extensions)
        if blob.content_type.startswith('image/'):
            image_url = f"https://storage.googleapis.com/{bucket_name}/{blob.name}"
            image_urls.append(image_url)

    return image_urls

# Example usage
device_id = "dev1"
date = "2023-07-30"  # Replace with the actual date
picture_file_path = "image.png"  # Replace with the actual picture file path

if __name__ == '__main__':
    dd = get_image_urls()
    dd = 4
