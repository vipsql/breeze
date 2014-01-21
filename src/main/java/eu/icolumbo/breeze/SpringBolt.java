package eu.icolumbo.breeze;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Spring for Storm bolts.
 * @author Pascal S. de Kloe
 */
public class SpringBolt extends SpringComponent implements ConfiguredBolt {

	private static final Logger logger = LoggerFactory.getLogger(SpringBolt.class);

	private boolean doAnchor = true;
	private String[] passThroughFields = {};

	private OutputCollector collector;


	public SpringBolt(Class<?> beanType, String invocation, String... outputFields) {
		super(beanType, invocation, outputFields);
	}

	@Override
	public void prepare(Map stormConf, TopologyContext topologyContext, OutputCollector outputCollector) {
		collector = outputCollector;
		super.init(stormConf, topologyContext);
	}

	/**
	 * Registers the {@link #setPassThroughFields(String...) pass through}
	 * and the {@link #getOutputFields() output field names}.
	 */
	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		List<String> names = new ArrayList<>();
		for (String f : getOutputFields()) names.add(f);
		for (String f : passThroughFields) names.add(f);
		declarer.declare(new Fields(names));
	}

	@Override
	public void execute(Tuple input) {
		logger.debug("{} got tuple '{}'", this, input.getMessageId());

		try {
			String[] inputFields = getInputFields();
			Object[] arguments = new Object[inputFields.length];
			for (int i = arguments.length; --i >= 0;
				arguments[i] = input.getValueByField(inputFields[i]));

			Values[] entries = invoke(arguments);
			String streamId = getOutputStreamId();
			logger.debug("{} provides {} tuples to stream {}",
					new Object[] {this, entries.length, streamId});

			for (Values output : entries) {
				for (String name : passThroughFields)
					output.add(input.getValueByField(name));

				if (doAnchor)
					collector.emit(streamId, input, output);
				else
					collector.emit(streamId, output);
			}

			collector.ack(input);
		} catch (InvocationTargetException e) {
			collector.reportError(e.getCause());
			collector.fail(input);
		}
	}

	@Override
	public void cleanup() {
	}

	/**
	 * Sets whether the tuple should be replayed in case of an error.
	 * @see <a href="https://github.com/nathanmarz/storm/wiki/Guaranteeing-message-processing">Storm Wiki</a>
	 */
	public void setDoAnchor(boolean value) {
		doAnchor = value;
	}

	@Override
	public String[] getPassThroughFields() {
		return passThroughFields;
	}

	@Override
	public void setPassThroughFields(String... value) {
		for (String name : value)
			for (String out : getOutputFields())
				if (name.equals(out))
					throw new IllegalArgumentException(name + "' already defined as output field");
		passThroughFields = value;
	}

}
