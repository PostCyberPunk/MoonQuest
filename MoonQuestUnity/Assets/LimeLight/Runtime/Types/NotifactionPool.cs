using UnityEngine;

namespace PCP.LibLime
{
	public class NotificationPool : MonoBehaviour
	{
		public Notification notificationPrefab;
		public int poolSize = 5;
		private Notification[] pool;
		private void Start()
		{
			pool = new Notification[poolSize];
			for (int i = 0; i < poolSize; i++)
			{
				pool[i] = Instantiate(notificationPrefab, transform);
				pool[i].gameObject.SetActive(false);
			}
		}
		public Notification Get()
		{
			foreach (Notification notification in pool)
			{
				if (!notification.gameObject.activeInHierarchy)
				{
					notification.gameObject.SetActive(true);
					return notification;
				}
			}
			return null;
		}
	}
}
