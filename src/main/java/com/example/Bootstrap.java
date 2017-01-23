package com.example;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.thoughtworks.xstream.XStream;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Created by rodrigo on 20/01/17.
 */
@Component
public class Bootstrap implements CommandLineRunner {

    @Override
    public void run(String... strings) throws Exception {
        //curso();;
        exemploUsandoTimer();
    }

    private void exemploUsandoTimer()throws Exception{

        final XStream xstream = new XStream();
        xstream.alias("negociacao",Negociacao.class);

        SimpleRegistry registro = new SimpleRegistry();
        registro.put("mysql",criaDatasource());

        CamelContext context = new DefaultCamelContext(registro);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("timer://foo?fixedRate=true&period=5s&delay=0&repeatCount=1").
                    to("http4://argentumws.caelum.com.br/negociacoes").
                        convertBodyTo(String.class).
                            unmarshal(new XStreamDataFormat(xstream)).
                            split(body()).
                                process(new Processor() {
                                    @Override
                                    public void process(Exchange exchange) throws Exception {
                                        Negociacao negociacao = exchange.getIn().getBody(Negociacao.class);
                                        exchange.setProperty("preco",negociacao.getPreco());
                                        exchange.setProperty("quantidade",negociacao.getQuantidade());
                                        String data = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(negociacao.getData().getTime());
                                        exchange.setProperty("data",data);
                                    }
                                }).
                            setBody(simple("insert into negociacao(preco,quantidade,data) values (${property.preco}, ${property.quantidade}, '${property.data}')")).

                        log("${body}").
                            to("jdbc:mysql").
                        end();
                        //setHeader(Exchange.FILE_NAME,constant("negociacoes.xml")).
                        //to("file:saida");
            }
        });

        context.start();
        Thread.sleep(11000);
        context.stop();
    }

    private void curso()throws Exception{
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder(){
            @Override
            public void configure() throws Exception {
                from("file:pedidos?delay=5s&noop=true").
                    setProperty("clienteId").xpath("/pedido/pagamento/email-titular/text()").
                    setProperty("pedidoId",xpath("/pedido/id/text()")).
                    split().xpath("/pedido/itens/item").
                        log("${id}").
                        filter().xpath("/item/formato[text()='EBOOK']").
                            setProperty("ebookId").xpath("/item/livro/codigo/text()").
                            marshal().xmljson().
                            log("${body}").
                            setHeader(Exchange.FILE_NAME,simple("${file:name.noext}-${header.CamelSplitIndex}.json")).
                            setHeader(Exchange.HTTP_METHOD, HttpMethods.GET).
                            setHeader(Exchange.HTTP_QUERY,simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clienteId}")).
                to("http4://localhost:8080/webservices/ebook/item");
            }
        });

        context.start();
        Thread.sleep(2000);
        context.stop();
    }

    public MysqlConnectionPoolDataSource criaDatasource(){
        MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
        dataSource.setDatabaseName("camel");
        dataSource.setServerName("localhost");
        dataSource.setPort(3306);
        dataSource.setUser("root");
        dataSource.setPassword("rodrigo007");
        return dataSource;
    }
}
