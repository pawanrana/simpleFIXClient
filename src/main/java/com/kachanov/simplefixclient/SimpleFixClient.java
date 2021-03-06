package com.kachanov.simplefixclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kachanov.simplefixclient.model.MessageF;

import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.FieldNotFound;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import quickfix.UnsupportedMessageType;
import quickfix.examples.banzai.BanzaiApplication;
import quickfix.examples.banzai.ExecutionTableModel;
import quickfix.examples.banzai.OrderTableModel;

public class SimpleFixClient extends BanzaiApplication {
	private static final Logger LOGGER = LoggerFactory.getLogger( SimpleFixClient.class );
	
	private static final String DEFINITIONS_FILE = "definitions.dsl";
	private static final String GROOVY_ENGINE_NAME = "groovy";

	private static Session _session = null;
	private static Connection _connection = null;
	private static ScriptEngine _groovyEngine = null;
	
	

	public static void main( String[] args ) throws InterruptedException, IOException {
		LOGGER.info( "Starting up..." );
		
		String fixConfigFile = validate( args );

		SimpleFixClient app = new SimpleFixClient( new OrderTableModel(), new ExecutionTableModel() );
		SessionSettings sessionSettings = null;
		SocketInitiator initiator = null;
		try {
			ScriptEngineManager factory = new ScriptEngineManager();
			_groovyEngine = factory.getEngineByName( GROOVY_ENGINE_NAME );
			
			sessionSettings = new SessionSettings( new FileInputStream( fixConfigFile ) );

			MessageStoreFactory storeFactory = new FileStoreFactory( sessionSettings );
			FileLogFactory logFactory = new FileLogFactory( sessionSettings );
			MessageFactory messageFactory = new DefaultMessageFactory();
			initiator = new SocketInitiator( app, storeFactory, sessionSettings, logFactory, messageFactory );
			initiator.start();

			Thread.sleep( 6000 );

			
			
			loadDSLDefinitions();

			for ( SessionID sessionId : initiator.getSessions() ) {
				LOGGER.info( sessionId.toString() );
				_session = Session.lookupSession( sessionId );
				_session.setRejectInvalidMessage( false );

				_connection = new Connection( _session );
				_session.logon();

				if (_session.isLoggedOn()) {

					String qualifier = sessionId.getSessionQualifier();
					File file = new File( "scenarios/" + qualifier + ".groovy" );
					FileReader fileReader = new FileReader( file );

					try {

						LOGGER.info( "Session is logged on" );

						Thread.sleep( 5_000 );

						Bindings bindings = new SimpleBindings();
						bindings.put( "connection", _connection );
						for ( MessageF msgType : MessageF.values() ) {
							bindings.put( msgType.name(), msgType );
						}

						LOGGER.info( "executing script for " + qualifier );

						_groovyEngine.setBindings( bindings, ScriptContext.ENGINE_SCOPE );
						_groovyEngine.eval( fileReader );
					} finally {
						fileReader.close();
						_session.logout();
					}

				} else {
					LOGGER.info( "Session is not logged on" );
				}

			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (ConfigError e) {
			e.printStackTrace();
		} catch (ScriptException e) {
			e.printStackTrace();
		} finally {
			if (initiator != null)
				initiator.stop();
		}
	}


	private static String validate( String[] args ) {
		String rv = "simplefixclient.cfg";
		if (args.length > 1) {
			throw new IllegalArgumentException( "only 1 parameter is allowed: name of fix config file" );
		} else {
			if (args.length != 0) rv = args[0].trim();
		}
		return rv;
	}

	public SimpleFixClient( OrderTableModel orderTableModel, ExecutionTableModel executionTableModel ) {
		super( orderTableModel, executionTableModel );
	}
	
	@Override
	public void fromApp( Message message, SessionID sessionID ) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
		// LOGGER.info( message.toString().replaceAll( "", "; " ) );
		_connection.addResponse( message );
	}

	private static void loadDSLDefinitions() throws IOException, ScriptException {
		LOGGER.info( "reading definitions from " + DEFINITIONS_FILE );
		List<String> l = Files.readAllLines( Paths.get( DEFINITIONS_FILE ) );
		_groovyEngine.eval( String.join( "\n", l ) );
	}

}
