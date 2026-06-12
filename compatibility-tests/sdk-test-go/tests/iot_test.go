package tests

import (
	"context"
	"strings"
	"testing"

	"floci-sdk-test-go/internal/testutil"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/iot"
	iottypes "github.com/aws/aws-sdk-go-v2/service/iot/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestIoT(t *testing.T) {
	ctx := context.Background()
	svc := testutil.IoTClient()

	t.Run("DescribeEndpoint", func(t *testing.T) {
		response, err := svc.DescribeEndpoint(ctx, &iot.DescribeEndpointInput{
			EndpointType: aws.String("iot:Data-ATS"),
		})
		require.NoError(t, err)
		assert.NotEmpty(t, aws.ToString(response.EndpointAddress))
	})

	t.Run("ThingRegistryCrud", func(t *testing.T) {
		thingName := "go-iot-thing"
		_, _ = svc.DeleteThing(ctx, &iot.DeleteThingInput{ThingName: aws.String(thingName)})

		_, err := svc.DescribeThing(ctx, &iot.DescribeThingInput{ThingName: aws.String(thingName)})
		require.Error(t, err)

		created, err := svc.CreateThing(ctx, &iot.CreateThingInput{
			ThingName: aws.String(thingName),
			AttributePayload: &iottypes.AttributePayload{
				Attributes: map[string]string{"env": "go"},
			},
		})
		require.NoError(t, err)
		assert.Equal(t, thingName, aws.ToString(created.ThingName))
		assert.True(t, strings.HasSuffix(aws.ToString(created.ThingArn), ":thing/"+thingName))

		_, err = svc.CreateThing(ctx, &iot.CreateThingInput{ThingName: aws.String(thingName)})
		require.Error(t, err)

		described, err := svc.DescribeThing(ctx, &iot.DescribeThingInput{ThingName: aws.String(thingName)})
		require.NoError(t, err)
		assert.Equal(t, "go", described.Attributes["env"])

		listed, err := svc.ListThings(ctx, &iot.ListThingsInput{})
		require.NoError(t, err)
		found := false
		for _, thing := range listed.Things {
			if aws.ToString(thing.ThingName) == thingName {
				found = true
			}
		}
		assert.True(t, found)

		_, err = svc.UpdateThing(ctx, &iot.UpdateThingInput{
			ThingName: aws.String(thingName),
			AttributePayload: &iottypes.AttributePayload{
				Attributes: map[string]string{"env": "updated", "owner": "iot"},
			},
		})
		require.NoError(t, err)

		updated, err := svc.DescribeThing(ctx, &iot.DescribeThingInput{ThingName: aws.String(thingName)})
		require.NoError(t, err)
		assert.Equal(t, "updated", updated.Attributes["env"])
		assert.Equal(t, "iot", updated.Attributes["owner"])

		_, err = svc.DeleteThing(ctx, &iot.DeleteThingInput{ThingName: aws.String(thingName)})
		require.NoError(t, err)

		_, err = svc.DescribeThing(ctx, &iot.DescribeThingInput{ThingName: aws.String(thingName)})
		require.Error(t, err)
	})
}
