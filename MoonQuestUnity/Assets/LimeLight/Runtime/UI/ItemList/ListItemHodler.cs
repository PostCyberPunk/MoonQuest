using TMPro;
namespace PCP.LibLime
{
	public interface IListHolder<T, U, V>
	{
		public TMP_Text TitleText { get; set; }
		public V GetID();
		public void UpdateItem(T data, U m);
		public void OnClick();
	}
}




