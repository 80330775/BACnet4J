package com.serotonin.bacnet4j.service.confirmed;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.TestUtils;
import com.serotonin.bacnet4j.npdu.test.TestNetwork;
import com.serotonin.bacnet4j.npdu.test.TestNetworkUtils;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.AddressBinding;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public class RemoveListElementRequestTest {
    private final Address addr = TestNetworkUtils.toAddress(2);
    private LocalDevice localDevice;

    @Before
    public void before() throws Exception {
        localDevice = new LocalDevice(1, new DefaultTransport(new TestNetwork(1, 0)));
        localDevice.writePropertyInternal(PropertyIdentifier.deviceAddressBinding,
                new SequenceOf<>( //
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 2), TestNetworkUtils.toAddress(2)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 3), TestNetworkUtils.toAddress(3)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 4), TestNetworkUtils.toAddress(4)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 5), TestNetworkUtils.toAddress(5)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 6), TestNetworkUtils.toAddress(6)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 7), TestNetworkUtils.toAddress(7)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 8), TestNetworkUtils.toAddress(8))));
        // Create an array of lists.
        localDevice.writePropertyInternal(PropertyIdentifier.forId(5555),
                new BACnetArray<>( //
                        new SequenceOf<>(new Real(0), new Real(1), new Real(2)), //
                        new SequenceOf<>(new Real(3), new Real(4)), //
                        new SequenceOf<>(new Real(5), new Real(6), new Real(7), new Real(8)), //
                        new SequenceOf<>(new CharacterString("a"), new CharacterString("b")), //
                        new Real(9)));
        localDevice.initialize();
    }

    @After
    public void after() {
        localDevice.terminate();
    }

    @Test // 15.2.1.3.1
    public void errorTypes() {
        // Ask for an object that doesn't exist.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.accessDoor, 0), //
                        PropertyIdentifier.absenteeLimit, //
                        null, //
                        new SequenceOf<>() //
                ).handle(localDevice, addr), ErrorClass.object, ErrorCode.unknownObject);

        // Ask for a property that isn't in the object.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.absenteeLimit, //
                        null, //
                        new SequenceOf<>() //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.unknownProperty);

        // Ask for a property that isn't in the object.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5556), //
                        null, //
                        new SequenceOf<>() //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.unknownProperty);

        // Specify a pin but for a property that isn't an array.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.deviceAddressBinding, //
                        new UnsignedInteger(1), //
                        new SequenceOf<>(new ObjectIdentifier(ObjectType.device, 2)) //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.propertyIsNotAnArray);

        // Specify a bad pin for an array property.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5555), //
                        new UnsignedInteger(0), //
                        new SequenceOf<>(new ObjectIdentifier(ObjectType.device, 2)) //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.invalidArrayIndex);

        // Specify a bad pin for an array property.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5555), //
                        new UnsignedInteger(6), //
                        new SequenceOf<>(new ObjectIdentifier(ObjectType.device, 2)) //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.invalidArrayIndex);

        // Specify a pin for an array property, where the element is not a list..
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5555), //
                        new UnsignedInteger(5), //
                        new SequenceOf<>(new ObjectIdentifier(ObjectType.device, 2)) //
                ).handle(localDevice, addr), ErrorClass.services, ErrorCode.propertyIsNotAList);

        // Specify a property that is an array, not a list.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5555), //
                        null, //
                        new SequenceOf<>() //
                ).handle(localDevice, addr), ErrorClass.services, ErrorCode.propertyIsNotAList);

        // Specify a property that is not a list.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.objectName, //
                        null, //
                        new SequenceOf<>() //
                ).handle(localDevice, addr), ErrorClass.services, ErrorCode.propertyIsNotAList);

        // Provide an element to add that is not right for the property.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.deviceAddressBinding, //
                        null, //
                        new SequenceOf<>(new ObjectIdentifier(ObjectType.device, 2)) //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.invalidDataType);

        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5555), //
                        new UnsignedInteger(4), //
                        new SequenceOf<>(new Real(0), new CharacterString("")) //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.invalidDataType);

        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5555), //
                        new UnsignedInteger(1), //
                        new SequenceOf<>(new Real(0), new CharacterString("")) //
                ).handle(localDevice, addr), ErrorClass.property, ErrorCode.invalidDataType);

        // Try to remove an element that doesn't exist.
        TestUtils.assertRequestHandleException( //
                () -> new RemoveListElementRequest( //
                        new ObjectIdentifier(ObjectType.device, 1), //
                        PropertyIdentifier.forId(5555), //
                        new UnsignedInteger(1), //
                        new SequenceOf<>(new Real(14)) //
                ).handle(localDevice, addr), ErrorClass.services, ErrorCode.listElementNotFound);
    }

    @Test
    public void list() throws Exception {
        // Remove a few elements.
        new RemoveListElementRequest( //
                new ObjectIdentifier(ObjectType.device, 1), //
                PropertyIdentifier.deviceAddressBinding, //
                null, //
                new SequenceOf<>(//
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 3), TestNetworkUtils.toAddress(3)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 5), TestNetworkUtils.toAddress(5)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 7), TestNetworkUtils.toAddress(7))) //
        ).handle(localDevice, addr);

        SequenceOf<AddressBinding> dabs = localDevice.getProperty(PropertyIdentifier.deviceAddressBinding);
        assertEquals(
                new SequenceOf<>( //
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 2), TestNetworkUtils.toAddress(2)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 4), TestNetworkUtils.toAddress(4)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 6), TestNetworkUtils.toAddress(6)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 8), TestNetworkUtils.toAddress(8))),
                dabs);

        // Remove a few more.
        new RemoveListElementRequest( //
                new ObjectIdentifier(ObjectType.device, 1), //
                PropertyIdentifier.deviceAddressBinding, //
                null, //
                new SequenceOf<>(//
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 2), TestNetworkUtils.toAddress(2)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 6), TestNetworkUtils.toAddress(6)),
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 8), TestNetworkUtils.toAddress(8))) //
        ).handle(localDevice, addr);

        dabs = localDevice.getProperty(PropertyIdentifier.deviceAddressBinding);
        assertEquals(
                new SequenceOf<>( //
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 4), TestNetworkUtils.toAddress(4))),
                dabs);

        // Remove the last one.
        new RemoveListElementRequest( //
                new ObjectIdentifier(ObjectType.device, 1), //
                PropertyIdentifier.deviceAddressBinding, //
                null, //
                new SequenceOf<>(//
                        new AddressBinding(new ObjectIdentifier(ObjectType.device, 4), TestNetworkUtils.toAddress(4))) //
        ).handle(localDevice, addr);

        dabs = localDevice.getProperty(PropertyIdentifier.deviceAddressBinding);
        assertEquals(new SequenceOf<>(), dabs);
    }

    @Test
    public void arrayOfList() throws Exception {
        // Replace all of the elements
        new RemoveListElementRequest( //
                new ObjectIdentifier(ObjectType.device, 1), //
                PropertyIdentifier.forId(5555), //
                new UnsignedInteger(3), //
                new SequenceOf<>(new Real(5), new Real(7), new Real(8)) //
        ).handle(localDevice, addr);

        SequenceOf<?> aol = localDevice.getProperty(PropertyIdentifier.forId(5555));
        assertEquals(new BACnetArray<>( //
                new SequenceOf<>(new Real(0), new Real(1), new Real(2)), //
                new SequenceOf<>(new Real(3), new Real(4)), //
                new SequenceOf<>(new Real(6)), //
                new SequenceOf<>(new CharacterString("a"), new CharacterString("b")), //
                new Real(9)), aol);

        // Only replace the second element
        new RemoveListElementRequest( //
                new ObjectIdentifier(ObjectType.device, 1), //
                PropertyIdentifier.forId(5555), //
                new UnsignedInteger(4), //
                new SequenceOf<>(new CharacterString("a"), new CharacterString("b")) //
        ).handle(localDevice, addr);

        aol = localDevice.getProperty(PropertyIdentifier.forId(5555));
        assertEquals(new BACnetArray<>( //
                new SequenceOf<>(new Real(0), new Real(1), new Real(2)), //
                new SequenceOf<>(new Real(3), new Real(4)), //
                new SequenceOf<>(new Real(6)), //
                new SequenceOf<>(), //
                new Real(9)), aol);
    }
}