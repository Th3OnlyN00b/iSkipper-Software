/**
 * 
 */
package com.csr460.iSkipper.emulator;

import com.csr460.iSkipper.device.AbstractSerialAdapter;
import com.csr460.iSkipper.device.SerialAdapter;
import com.csr460.iSkipper.handler.AttackHandler;
import com.csr460.iSkipper.handler.CaptureHandler;
import com.csr460.iSkipper.handler.PrintHandler;
import com.csr460.iSkipper.handler.ReceivedPacketHandlerInterface;
import com.csr460.iSkipper.support.Answer;
import com.csr460.iSkipper.support.AnswerPacketHashMap;
import com.csr460.iSkipper.support.IClickerChannel;
import com.csr460.iSkipper.support.IClickerID;
import com.csr460.iSkipper.support.Transcoding;

/**
 * @author CSR
 *
 *         The main emulator class.
 */
public class Emulator
{
	private static final int SERIAL_WAIT_TIME = 5000;
	private AbstractSerialAdapter serial;
	private volatile EmulatorModes mode;
	private volatile ReceivedPacketHandlerInterface handler;
	private volatile IClickerID emulatorID;
	private volatile IClickerChannel emulatorChannel;

	public Emulator(SerialAdapter serialPort)
	{
		if (serialPort == null)
			throw new NullPointerException("Serial port object is null when constructing an Emulator object.\n");
		if (!serialPort.isAvailable())
			throw new IllegalArgumentException("Serial port is not available when constructing an Emulator object.\n");
		this.serial = serialPort;
		this.mode = EmulatorModes.DISCONNECTED;
		this.handler = new PrintHandler();
	}

	/**
	 * The method to confirm connection with the iSkipper hardware.
	 * 
	 * @return whether successfully initialized
	 */
	public boolean initialize()
	{
		if (mode != EmulatorModes.DISCONNECTED)
			return false;
		serial.writeByte(SerialSymbols.OP_COMFIRM_CONNECTION);// Send command
		serial.setPacketHandler((packet) ->
		{
			// This packet handler are called in another thread.
			System.out.print(packet);
			if (mode.equals(EmulatorModes.STANDBY))
				return;
			try
			{
				// There should be one line contains the fixed ID
				IClickerID id = IClickerID
						.idFromString(packet.toString().substring(0, IClickerID.ID_HEX_STRING_LENGTH));
				if (id != null)
				{
					emulatorID = id;
					mode = EmulatorModes.STANDBY;
					emulatorChannel = IClickerChannel.AA;// Default AA channel in Arduino Sketch.
					wakeEmulator();
				}
			} catch (Exception e)
			{
				// keep going
			}
		});
		waitForHandler();
		serial.setPacketHandler(handler);
		return mode == EmulatorModes.STANDBY;
	}

	/**
	 * Start the capturing mode. Must be STANDBY mode to start capturing.
	 * 
	 * @param storageHashMap
	 *            the HashMap to storage captured answers.
	 * @param shouldPrintRaw
	 *            Whether print the raw data when receiving a response or not.
	 * @param shouldPrintStatis
	 *            Whether print the statistic of the answers when receiving a
	 *            response or not.
	 * @return Whether successfully switch to Capturing mode.
	 * 
	 * @see CaptureHandler#CaptureHandler(AnswerPacketHashMap, boolean, boolean);
	 */
	public boolean startCapture(AnswerPacketHashMap storageHashMap, boolean shouldPrintRaw, boolean shouldPrintStatis)
	{
		if (mode != EmulatorModes.STANDBY)
			return false;
		serial.writeByte(SerialSymbols.OP_CAPTURE);
		serial.setPacketHandler((packet) ->
		{

			if (packet.dataContains(SerialSymbols.RES_SUCCESS))
			{
				handler = new CaptureHandler(storageHashMap, shouldPrintRaw, shouldPrintStatis);
				mode = EmulatorModes.CAPTURE;
				wakeEmulator();
				return;
			}
			System.out.print(packet);
		});
		waitForHandler();
		serial.setPacketHandler(handler);
		return mode == EmulatorModes.CAPTURE;
	}

	/**
	 * Start the capturing mode. Must be STANDBY mode to start capturing.
	 * 
	 * @param captureHandler
	 *            The CaptureHandler to handle the incoming answers.
	 * @return Whether successfully switch to Capturing mode.
	 * 
	 * @see CaptureHandler#CaptureHandler(AnswerPacketHashMap, boolean, boolean);
	 */
	public boolean startCapture(CaptureHandler captureHandler)
	{
		if (handler == null)
			throw new NullPointerException("CaptureHandler Cannot Be Null!");
		if (mode != EmulatorModes.STANDBY)
			return false;
		serial.writeByte(SerialSymbols.OP_CAPTURE);
		serial.setPacketHandler((packet) ->
		{

			if (packet.dataContains(SerialSymbols.RES_SUCCESS))
			{
				handler = captureHandler;
				mode = EmulatorModes.CAPTURE;
				wakeEmulator();
				return;
			}
			System.out.print(packet);
		});
		waitForHandler();
		serial.setPacketHandler(handler);
		return mode == EmulatorModes.CAPTURE;
	}

	/**
	 * Stop the current mode and change to STANDBY mode. It would return true if it
	 * was already in STANDBY mode.
	 * 
	 * @return Whether successfully stop current mode and switch to STANDBY mode.
	 */
	public boolean stopAndGoStandby()
	{
		if (mode == EmulatorModes.STANDBY)
			return true;
		else if (mode == EmulatorModes.DISCONNECTED)
			return false;
		serial.writeByte(SerialSymbols.OP_STOP);
		serial.setPacketHandler((packet) ->
		{
			if (packet.dataContains(SerialSymbols.RES_STANDBY))
			{
				handler = new PrintHandler();
				mode = EmulatorModes.STANDBY;
				wakeEmulator();
				return;
			}
			System.out.print(packet);
		});
		waitForHandler();
		serial.setPacketHandler(handler);
		return mode == EmulatorModes.STANDBY;
	}

	/**
	 * @param channel
	 *            The target channel to change to.
	 * @return Whether successfully change to the desired channel.
	 */
	public boolean changeChannel(IClickerChannel channel)
	{
		if (mode != EmulatorModes.STANDBY)
			return false;
		if (channel == null)
			throw new NullPointerException("Null channel when changing channel.");
		String toSend = (char) SerialSymbols.OP_CHANGE_CHANNEL + "," + channel.toString();
		serial.writeBytes(Transcoding.stringToBytes(toSend));
		serial.setPacketHandler((packet) ->
		{
			if (packet.dataContains(SerialSymbols.RES_SUCCESS))
			{
				emulatorChannel = channel;
				wakeEmulator();
				return;
			}
		});
		mode = EmulatorModes.BUSY;
		waitForHandler();
		mode = EmulatorModes.STANDBY;
		serial.setPacketHandler(handler);
		return emulatorChannel == channel;
	}

	/**
	 * Submitting answer with the fixed ID of this emulator.
	 * 
	 * @param answer
	 *            The answer
	 * @return Whether successfully submitted.
	 */
	public boolean submitAnswer(Answer answer)
	{
		if (mode != EmulatorModes.STANDBY)
			return false;
		if (answer == null)
			throw new NullPointerException("Null answer when submitting an answer.");
		String toSend = (char) SerialSymbols.OP_ANSWER + "," + answer.toString();
		serial.writeBytes(Transcoding.stringToBytes(toSend));
		serial.setPacketHandler((packet) ->
		{
			if (packet.dataContains(SerialSymbols.RES_STANDBY))
			{
				wakeEmulator();
				return;
			}
			System.out.print(packet);
		});
		mode = EmulatorModes.ANSWER;
		waitForHandler();
		mode = EmulatorModes.STANDBY;
		serial.setPacketHandler(handler);
		return true;
	}

	/**
	 * Enter SUBMIT mode, in this mode the com.csr460.iSkipper.device would keep listen serial port for
	 * input IDs and answers to submit. And write "ACK\n" when one answer is sent.
	 * The answers and IDs can be sent by {@link #submitAnswer(Answer)} method.
	 * 
	 * Call {@link #stopAndGoStandby()} to stop SUBMIT mode.
	 * 
	 * @param handler
	 *            The handler for submit mode.
	 * @return Whether successfully enter SUBMIT mode.
	 */
	public boolean startSubmitMode(final ReceivedPacketHandlerInterface handler)
	{
		if (mode != EmulatorModes.STANDBY)
			return false;
		if (handler == null)
			throw new NullPointerException("Handler cannot be null when start submit mode.");
		serial.writeByte(SerialSymbols.OP_SUBMIT);
		serial.setPacketHandler(packet ->
		{
			if (packet.dataContains(SerialSymbols.RES_SUCCESS))
			{
				this.handler = handler;
				mode = EmulatorModes.SUBMIT;
				wakeEmulator();
				return;
			}
			System.out.print(packet);
		});
		mode = EmulatorModes.BUSY;
		waitForHandler();
		if (mode != EmulatorModes.SUBMIT)
		{
			this.handler = new PrintHandler();
			mode = EmulatorModes.STANDBY;
		}
		serial.setPacketHandler(this.handler);
		return mode == EmulatorModes.SUBMIT;
	}

	/**
	 * Submit answers with specific IDs in SUBMIT mode. Use
	 * {@link #startSubmitMode(ReceivedPacketHandlerInterface)} to enter SUBMIT
	 * mode.
	 * 
	 * @param id
	 *            The id of the submitted packet.
	 * @param answer
	 *            The answer to submit
	 */
	public synchronized void submitInSubmitMode(IClickerID id, Answer answer)
	{

		if (id == null || answer == null)
			throw new NullPointerException("ID and Answer cannot be null");
		if (mode != EmulatorModes.SUBMIT)
			throw new IllegalStateException("submitInSubmitMode() must be used in SUBMIT mode!");
		StringBuilder builder = new StringBuilder();
		// Format: <Answer>,<ID>\0
		builder.append(answer.toString());
		builder.append(',');
		builder.append(id.toString());
		serial.writeBytes(Transcoding.stringToBytes(builder.toString()));
	}

	/**
	 * @param answer
	 *            The answer to submit repeatedly. Random answer if NULL.
	 * @param count
	 *            How many time you want to submit
	 * @param handler
	 *            The AttackHandler to handle the response from the com.csr460.iSkipper.device during
	 *            attacking.
	 * @return Whether successfully start attacking.
	 */
	public boolean startAttack(Answer answer, long count, AttackHandler handler)
	{
		if (mode != EmulatorModes.STANDBY)
			return false;
		if (count < 0)
			throw new IllegalArgumentException("Count cannot be negative when attacking.");
		if (handler == null)
			throw new NullPointerException("Null handler when start attack.");
		String strAns = answer == null ? "R" : answer.toString();
		String toSend = (char) SerialSymbols.OP_ATTACK + "," + strAns + "," + String.valueOf(count);
		serial.writeBytes(Transcoding.stringToBytes(toSend));
		serial.setPacketHandler((packet) ->
		{
			if (packet.dataContains(SerialSymbols.RES_SUCCESS))
			{
				mode = EmulatorModes.ATTACK;
				wakeEmulator();
				return;
			}
		});
		waitForHandler();
		serial.setPacketHandler(handler);
		return mode == EmulatorModes.ATTACK;
	}

	/**
	 * @return the serial adapter
	 */
	public AbstractSerialAdapter getSerial()
	{
		return serial;
	}

	/**
	 * @param serial
	 *            the serial port to set
	 */
	public void setSerial(AbstractSerialAdapter serial)
	{
		this.serial = serial;
	}

	/**
	 * @return Whether the emulator is ready to use.
	 */
	public boolean isAvailable()
	{
		return mode == EmulatorModes.STANDBY;
	}

	/**
	 * @return the mode
	 */
	public EmulatorModes getMode()
	{
		return mode;
	}

	/**
	 * @param mode
	 *            the mode to set
	 */
	public void setMode(EmulatorModes mode)
	{
		this.mode = mode;
	}

	/**
	 * @return the serial packet handler
	 */
	public ReceivedPacketHandlerInterface getHandler()
	{
		return handler;
	}

	/**
	 * @return the fixed iClickerID of this emulator from the hardware.
	 */
	public IClickerID getEmulatorID()
	{
		return emulatorID;
	}

	/**
	 * @return the current channel that being used by this emulator.
	 */
	public IClickerChannel getEmulatorChannel()
	{
		return emulatorChannel;
	}

	/**
	 * Used to suspend the current thread to wait for handler to done its process.
	 */
	private synchronized void waitForHandler()
	{
		try
		{
			this.wait(SERIAL_WAIT_TIME);
		} catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Used in handler class when it finished its job and resume the thread.
	 */
	private synchronized void wakeEmulator()
	{
		this.notifyAll();
	}

}
