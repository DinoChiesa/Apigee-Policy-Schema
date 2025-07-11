## Example Validator in Python

This is a validator that uses Python 3.13 and the xmlschema module
to validate policy, endpoint, and sharedflow files against the Apigee schema.

### Pre-requisites

- Python 3.13
- xmlschema module (see the requirements.txt file)

### Setup

To use the script, you'll need to install its dependencies. It's a good idea to
use a Python virtual environment.

1.  **Create and activate a virtual environment:**

    ```bash
    python3 -m venv venv
    source venv/bin/activate
    ```

2.  **Install the required packages:**

    ```bash
    pip install -r requirements.txt
    ```

### Using it

Once the dependencies are installed, you can run the script like this:

```bash
python ./validatePolicy.py --xsd ../schemas/TheSchema.xsd --xml /path/to/your/policy.xml
```

For example, to validate a sample `AssignMessage` policy:

```bash
python ./validatePolicy.py --xsd ../schema/AssignMessage.xsd --xml /path/to/your/assign-message-policy.xml
```

To validate all of the policies as well as the endoints configured within a bundle:

```bash
python validateBundle.py --xsdSource ../schemas --source /path/to/your/apiproxy
```

You can also specify a zipped bundle (apiproxy or sharedflo):

```bash
python validateBundle.py --xsdSource ../schemas --source /path/to/your/apiproxy-or-sharedflow-bundle.zip
```
