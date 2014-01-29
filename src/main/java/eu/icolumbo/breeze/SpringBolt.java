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
 * @author Jethro Bakker
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
		declarer.declareStream(getOutputStreamId(), new Fields(names));
	}

	@Override
	public void execute(Tuple input) {
		logger.debug("{} got tuple '{}'", this, input.getMessageId());

		try {
			String[] inputFields = getInputFields();
			Object[] arguments = new Object[inputFields.length];
			for (int i = arguments.length; --i >= 0;
				arguments[i] = input.getValueByField(inputFields[i]));

			Object[] returnEntries = invoke(method, arguments);
			Values[] entries = new Values[returnEntries.length];
			for(int i=0; i < returnEntries.length; i++){
				entries[i] = getMapping(returnEntries[i], getOutputFields());
			}

			logger.trace("Got {} results", entries.length);

			if (entries.length == 0 && passThroughFields.length != 0 && ! getScatterOutput()) {
				logger.trace("Pass through only");
				entries = new Values[] {new Values()};
			}

			String streamId = getOutputStreamId();
			logger.debug("{} provides {} tuples to stream {}",
					new Object[] {this, entries.length, streamId});

			for (Values output : entries) {
				for (String name : passThroughFields)
					output.add(input.getValueByField(name));

				logger.trace("Tuple emit");
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
