from botocore.exceptions import ClientError


def test_describe_endpoint(iot_client):
    response = iot_client.describe_endpoint(endpointType="iot:Data-ATS")

    assert response["endpointAddress"]


def test_thing_registry_crud(iot_client, unique_name):
    thing_name = f"{unique_name}-thing"

    missing = False
    try:
        iot_client.describe_thing(thingName=thing_name)
    except ClientError as exc:
        missing = exc.response["Error"]["Code"] == "ResourceNotFoundException"
    assert missing

    created = iot_client.create_thing(
        thingName=thing_name,
        attributePayload={"attributes": {"env": "python"}},
    )
    assert created["thingName"] == thing_name
    assert created["thingArn"].endswith(f":thing/{thing_name}")

    try:
        iot_client.create_thing(thingName=thing_name)
        raise AssertionError("duplicate create should fail")
    except ClientError as exc:
        assert exc.response["Error"]["Code"] == "ResourceAlreadyExistsException"

    described = iot_client.describe_thing(thingName=thing_name)
    assert described["attributes"]["env"] == "python"

    listed = iot_client.list_things()
    assert any(thing["thingName"] == thing_name for thing in listed["things"])

    iot_client.update_thing(
        thingName=thing_name,
        attributePayload={"attributes": {"env": "updated", "owner": "iot"}},
    )
    updated = iot_client.describe_thing(thingName=thing_name)
    assert updated["attributes"] == {"env": "updated", "owner": "iot"}

    iot_client.delete_thing(thingName=thing_name)
    try:
        iot_client.describe_thing(thingName=thing_name)
        raise AssertionError("describe after delete should fail")
    except ClientError as exc:
        assert exc.response["Error"]["Code"] == "ResourceNotFoundException"
