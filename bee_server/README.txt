install dependencies:
pip install -r requirements.txt

# run demo
python server.py

# run with nohup
nohup python server.py &

# see and kill procs
netstat -ltnp | grep -w ':5000'
kill -9 12345

sudo kill -9 $(sudo lsof -t -i:5000)

# deploy to server:
1. enter the GCP console and access VM terminal via SSH-in-browser
2. stop the current flask service running on port 5000:
    - sudo kill -9 $(sudo lsof -t -i:5000)
3. pull latest changes from GitHub into /home/hivemonitoring1/bee_ai : 
    - git pull
4. start a new flask service with the latest changes:
    - cd /home/hivemonitoring1/bee_ai/bee_server
    - nohup python server.py &
5. close terminal


TODO:
    - use async wrapper for Flask by combining it with an asynchronous server or using libraries that support asynchronous programming