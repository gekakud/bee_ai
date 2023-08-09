import requests

api_url = "http://127.0.0.1:5000/api/upload"  # Update with the correct URL
image_path = "image copy.png"

with open(image_path, "rb") as image_file:
    files = {"image": image_file}
    response = requests.post(api_url, files=files)
    dd = 4

print(response.text)
