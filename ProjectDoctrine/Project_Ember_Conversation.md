
# **Project Ember** - Conversation Archive

## **Conversation Summary**

### **User's Plan**:

- **Main Brain**: **DistilGPT-2** (code name "Ember").
- **Sub-minds**: 
  - **Sub1**: **GPT-Neo 1.3B**.
  - **Sub2**: **T5**.
- **Extra Sub-models** (for scalability): 
  - **Sub3**: Additional **GPT-Neo 1.3B** or **T5**.
  - **Sub4**: Additional **GPT-Neo 1.3B** or **T5**.
- **Workflow**: 
  - Ember (DistilGPT-2) interacts with the user.
  - Ember decides which sub-model (Sub1 or Sub2) to activate.
  - Sub-models complete their tasks and return to "sleep".
- **Commitment**: This plan is the one to follow, and deviation will only happen with explicit user permission.

## **Conversation Details**:

### **User’s Plan and How We’re Achieving It**:

- **User's Request**: Set up a local-only system with Ember as the main brain, and specialized sub-models (GPT-Neo 1.3B, T5) for task handling.
- **Implementation**: Discussed the setup for **Ember** (DistilGPT-2) as the interface, deciding which model to wake up (Sub1 or Sub2) for the task at hand, with additional sub-models (Sub3 and Sub4) for scalability.

### **How the System is Designed**:

- **Main Brain**: **DistilGPT-2** (Ember) handles all interactions.
- **Sub-Models**: Fine-tuning and downloading **GPT-Neo 1.3B** for specialized tasks like circuit design and **T5** for project management.
- **Extra Models**: Set up additional instances of GPT-Neo 1.3B or T5 (Sub3/Sub4) for scalability.
- **Task Delegation**: Ember decides which sub-model to load and handles communication between the front-end and back-end.

### **Implementation Discussion**:

- **Web Interface**: Flask or FastAPI will be used to serve the web interface and API.
- **Back-End**: Local Flask app running on **localhost**, handling model switching and communication.
- **Model Backups**: Plan for local backups of both original and fine-tuned models on the **128GB AI drive**.
- **Local Deployment**: All models and the interface will run locally, accessible through the browser.
