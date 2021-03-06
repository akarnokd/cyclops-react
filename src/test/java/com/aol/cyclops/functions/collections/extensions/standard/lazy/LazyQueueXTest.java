package com.aol.cyclops.functions.collections.extensions.standard.lazy;

import com.aol.cyclops.data.collections.extensions.CollectionX;
import com.aol.cyclops.data.collections.extensions.standard.QueueX;
import com.aol.cyclops.functions.collections.extensions.AbstractLazyTest;

public class LazyQueueXTest extends AbstractLazyTest{

	@Override
	public <T> CollectionX<T> of(T... values) {
		return QueueX.of(values);
	}
	/* (non-Javadoc)
	 * @see com.aol.cyclops.functions.collections.extensions.AbstractCollectionXTest#empty()
	 */
	@Override
	public <T> CollectionX<T> empty() {
		return QueueX.empty();
	}

}
