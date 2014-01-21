package eu.icolumbo.breeze.connect;

import eu.icolumbo.breeze.ConfiguredSpout;
import eu.icolumbo.breeze.FunctionSignature;

import backtype.storm.drpc.DRPCSpout;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;

import static backtype.storm.utils.Utils.DEFAULT_STREAM_ID;
import static java.lang.String.format;


/**
 * @author Pascal S. de Kloe
 */
public class SpringRPCRequest extends DRPCSpout implements ConfiguredSpout {

	private final String ID;
	private final String[] outputFields;
	private final String DESCRIPTION;

	private Number parallelism;


	public SpringRPCRequest(String signature) {
		this(FunctionSignature.valueOf(signature));
	}

	private SpringRPCRequest(FunctionSignature signature) {
		super(signature.getFunction());
		ID = signature.getFunction() + "-rpc-req";
		DESCRIPTION = format("%s '%s'", getClass().getSimpleName(), signature.getFunction());

		String outputField = "_ignoreArguments";
		if (signature.getArguments().length == 1) {
			outputField = signature.getArguments()[0];
		} else if (signature.getArguments().length != 0) {
			String msg = "Breeze RPC supports only one argument for now";
			throw new UnsupportedOperationException(msg);
		}
		String requestContextField = getContextField(signature);

		outputFields = new String[] {outputField, requestContextField};
	}


	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		Fields fields = new Fields(getOutputFields());
		declarer.declareStream(getOutputStreamId(), fields);
	}

	@Override
	public String toString() {
		return DESCRIPTION;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public String getOutputStreamId() {
		return DEFAULT_STREAM_ID;
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public Number getParallelism() {
		return parallelism;
	}

	public void setParallelism(Number value) {
		parallelism = value;
	}

	/**
	 * Gets the field name name for the {@link SpringRPCResponse response spout}.
	 */
	static String getContextField(FunctionSignature signature) {
		return signature.getFunction() + "-rpc-ctx";
	}

}
