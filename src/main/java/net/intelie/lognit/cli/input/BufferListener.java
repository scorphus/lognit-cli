package net.intelie.lognit.cli.input;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import net.intelie.lognit.cli.http.RestListener;
import net.intelie.lognit.cli.model.Message;
import net.intelie.lognit.cli.model.MessageBag;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BufferListener implements RestListener<MessageBag> {
    public static final String NO_CLUSTER_INFO = "WARN: seems there is a bug in server response, no cluster info";
    public static final String MISSING_NODES_RESPONSE = "WARN: missing some cluster responses, check nodes status";
    private final Deque<MessageBag> historic;
    private final Deque<MessageBag> other;
    private final Semaphore semaphore;
    private final MessagePrinter printer;
    private boolean releasing;

    @Inject
    public BufferListener(MessagePrinter printer) {
        this.printer = printer;
        this.historic = new LinkedList<MessageBag>();
        this.other = new LinkedList<MessageBag>();
        this.semaphore = new Semaphore(0);
        this.releasing = false;
    }


    @Override
    public synchronized void receive(MessageBag messages) {
        if (releasing) {
            printBag(messages);
            return;
        }
        if (messages.isHistoric()) {
            historic.add(messages);
            semaphore.release();
        } else {
            other.add(messages);
        }
    }

    public boolean waitHistoric(int timeout, int releaseMax) {
        boolean success = false;
        try {
            if (!semaphore.tryAcquire(1, timeout, TimeUnit.MILLISECONDS)) {
                printer.printStatus(MISSING_NODES_RESPONSE);
                return false;
            }
            int waiting = historic.getFirst().getTotalNodes() - 1;
            if (waiting < 0) {
                printer.printStatus(NO_CLUSTER_INFO);
                Thread.sleep(timeout);
                success = false;
            } else {
                success = semaphore.tryAcquire(waiting, timeout, TimeUnit.MILLISECONDS);
                if (!success) {
                    printer.printStatus(MISSING_NODES_RESPONSE);
                }
            }
        } catch (InterruptedException ex) {
            printer.printStatus(MISSING_NODES_RESPONSE);
        }
        releaseHistoric(releaseMax);
        return success;
    }

    private void releaseHistoric(int releaseMax) {
        List<Message> reverse = pickValidHistory(releaseMax);

        for (Message message : reverse)
            printer.printMessage(message);
    }

    private List<Message> pickValidHistory(int releaseMax) {
        PriorityQueue<Message> queue = new PriorityQueue<Message>();

        while (!historic.isEmpty()) {
            MessageBag bag = historic.pop();
            queue.addAll(bag.getItems());
        }

        Iterable<Message> limited = Iterables.limit(queue, releaseMax);
        LinkedList<Message> list = Lists.newLinkedList(limited);
        return Lists.reverse(list);
    }

    public synchronized void releaseAll() {
        releasing = true;
        while (!other.isEmpty()) {
            MessageBag bag = other.pop();
            printBag(bag);
        }
    }

    private void printBag(MessageBag bag) {
        for (Message message : Lists.reverse(bag.getItems()))
            printer.printMessage(message);
    }
}
