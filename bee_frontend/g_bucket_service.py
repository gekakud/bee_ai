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

import datetime

def get_image_urls():
    client = initialize_storage_client()
    bucket = client.get_bucket(bucket_name)
    
    image_links = []
    blobs = bucket.list_blobs()

    for blob in blobs:
        if blob.content_type.startswith('image/'):
            # Extract the image name from the blob name
            image_name = blob.name.split('/')[-1]
            
            # Set the expiration time and generate the signed URL
            expiration_time = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)
            image_url = blob.generate_signed_url(expiration=expiration_time)

            # Create a dictionary containing the image name and URL, or any other structure you prefer
            image_link = {'name': image_name, 'url': image_url}
            image_links.append(image_link)

    return image_links



# Example usage
device_id = "dev1"
date = "2023-07-30"  # Replace with the actual date
picture_file_path = "image.png"  # Replace with the actual picture file path

if __name__ == '__main__':
    dd = get_image_urls()
    dd = 4
