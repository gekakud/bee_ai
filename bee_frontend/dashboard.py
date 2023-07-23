import streamlit as st

mongo_uri = "mongodb+srv://bee_admin:bee_admin_pass@atlascluster.d85negs.mongodb.net/?retryWrites=true&w=majority"

def load_data():
    pass

with st.sidebar:
    hive_selection = st.selectbox('Choose hive')
