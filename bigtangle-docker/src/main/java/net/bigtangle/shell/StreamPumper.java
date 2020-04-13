package net.bigtangle.shell;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * Class to pump the error stream during Process's runtime. Copied from the Ant
 * built-in task.
 *
 * @author <a href="mailto:fvancea@maxiq.com">Florin Vancea </a>
 * @author <a href="mailto:pj@thoughtworks.com">Paul Julius </a>
 * @since June 11, 2001
 * @version $Id: StreamPumper.java 5958 2007-02-28 10:29:55Z olamy $
 */
public class StreamPumper
    extends Thread
{
    private BufferedReader in;

    private StreamConsumer consumer = null;

    private PrintWriter out = null;

    private static final int SIZE = 1024;

    boolean done;

    public StreamPumper( InputStream in )
    {
        this.in = new BufferedReader( new InputStreamReader( in ), SIZE );
    }

    public StreamPumper( InputStream in, StreamConsumer consumer )
    {
        this( in );

        this.consumer = consumer;
    }

    public StreamPumper( InputStream in, PrintWriter writer )
    {
        this( in );

        out = writer;
    }

    public StreamPumper( InputStream in, PrintWriter writer, StreamConsumer consumer )
    {
        this( in );
        this.out = writer;
        this.consumer = consumer;
    }

    public void run()
    {
        try
        {
            String s = in.readLine();

            while ( s != null )
            {
                consumeLine( s );

                if ( out != null )
                {
                    out.println( s );

                    out.flush();
                }

                s = in.readLine();
            }
        }
        catch ( Throwable e )
        {
            // Catched everything so the streams will be closed and flagged as done.
        }
        finally
        {
            IOUtil.close( in );

            done = true;

            synchronized ( this )
            {
                this.notifyAll();
            }
        }
    }

    public void flush()
    {
        if ( out != null )
        {
            out.flush();
        }
    }

    public void close()
    {
        IOUtil.close( out );
    }

    public boolean isDone()
    {
        return done;
    }

    private void consumeLine( String line )
    {
        if ( consumer != null )
        {
            consumer.consumeLine( line );
        }
    }
}
