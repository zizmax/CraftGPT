# Google Gemini Integration Setup

This document explains how to set up the Google Gemini integration for CraftGPT.

## Prerequisites

1. A Google Cloud Platform account
2. A project with the Vertex AI API enabled
3. A service account with the necessary permissions

## Setup Instructions

### 1. Create a Google Cloud Project

If you don't already have a project:
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click on "Select a project" at the top of the page
3. Click "New Project"
4. Enter a project name and click "Create"

### 2. Enable the Vertex AI API

1. Go to [APIs & Services > Library](https://console.cloud.google.com/apis/library)
2. Search for "Vertex AI API"
3. Click on "Vertex AI API"
4. Click "Enable"

### 3. Create a Service Account

1. Go to [IAM & Admin > Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts)
2. Click "Create Service Account"
3. Enter a name and description
4. Click "Create and Continue"
5. Add the "Vertex AI User" role
6. Click "Continue" and then "Done"

### 4. Create a Service Account Key

1. Find your service account in the list
2. Click on the three dots menu at the end of the row
3. Select "Manage keys"
4. Click "Add Key" > "Create new key"
5. Select "JSON" and click "Create"
6. The key file will be downloaded to your computer. Keep this file secure!

### 5. Configure CraftGPT

1. Move the downloaded key file to a secure location on your server
2. Update your `config.yml` file with the following settings:

```yaml
# Set the AI provider to gemini
ai-provider: "gemini"

# Google Gemini API Configuration
gemini-credentials-path: "/path/to/your/google-cloud-key.json"
gemini-project-id: "your-google-cloud-project-id" # Found in the key file or GCP console
gemini-location: "us-central1" # Or another supported region
gemini-model: "gemini-1.5-pro" # Or another Gemini model
```

3. Restart your server

## Available Models

The following Gemini models are available:

- `gemini-1.5-pro` - Recommended for most use cases
- `gemini-1.5-flash` - Faster, more cost-effective model
- `gemini-pro` - Legacy model
- `gemini-flash` - Legacy model

## Troubleshooting

If you encounter issues:

1. Check the server logs for detailed error messages
2. Verify that your service account has the correct permissions
3. Make sure the path to the credentials file is correct
4. Confirm that your project has the Vertex AI API enabled
5. Ensure your Google Cloud account has billing enabled

For more help, visit the CraftGPT Discord server or submit an issue on GitHub.